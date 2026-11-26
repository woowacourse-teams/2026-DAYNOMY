"""Real Nginx reload and POSIX ownership handoff on an isolated Docker network."""
import json
import os
import subprocess
import threading
import time
from pathlib import Path


class HandoffLab:
    def __init__(self, command, prefix, app, nginx, volume, output):
        self.command = command
        self.app, self.nginx, self.volume, self.output = app, nginx, volume, output
        self.probe = prefix + '-route-probe'
        self.cancel = threading.Event()
        self.errors = []
        self.transitions = []
        self.initialized_ms = None
        self.thread = None
        self.process = None
        self.probe_log = None

    def control(self, script, *args):
        return self.command('docker', 'run', '--rm', '--network', 'container:' + self.app,
                            '--pid', 'container:' + self.app, '-v', self.volume + ':/deploy-state',
                            'python:3.13-slim', 'python', '-c', script, *args)

    def initialize(self):
        self.command('docker', 'volume', 'create', self.volume)
        script = """
from pathlib import Path
p=Path('/deploy-state'); (p/'active-slot').write_text('blue\\n'); (p/'jobs.lock').touch()
(p/'upstream.conf').write_text('upstream app { server 127.0.0.1:8080; }\\n')
(p/'nginx.conf').write_text('pid /deploy-state/nginx.pid; error_log /dev/stderr warn; events { worker_connections 1024; } http { access_log off; include /deploy-state/upstream.conf; server { listen 8090; location / { proxy_pass http://app; } } }')
"""
        self.command('docker', 'run', '--rm', '--network', 'none', '-v', self.volume + ':/deploy-state',
                     'python:3.13-slim', 'python', '-c', script)

    def start(self, duration):
        self.initialized_ms = time.time_ns()//1000000
        self.command('docker', 'run', '-d', '--name', self.nginx, '--network', 'container:' + self.app,
                     '--pid', 'container:' + self.app, '-v', self.volume + ':/deploy-state',
                     'nginx:1.28-alpine', 'nginx', '-c', '/deploy-state/nginx.conf', '-g', 'daemon off;')
        self.control("import urllib.request; r=urllib.request.urlopen('http://127.0.0.1:8090/api/news?page=1&size=1'); assert r.headers['X-Daynomy-Slot']=='blue'")
        probe = Path(__file__).resolve().parents[1] / 'loadtest/deployment-availability.js'
        self.probe_log = (self.output/'route-k6.log').open('w')
        self.process = subprocess.Popen([
            'docker','run','--rm','--name',self.probe,'--network','container:'+self.app,
            '--user',str(os.getuid()),
            '-v',str(probe)+':/probe.js:ro','-v',str(self.output)+':/out',
            'grafana/k6:latest','run','--quiet','--log-format','raw',
            '--console-output','/out/route-samples.jsonl','-e','BASE_URL=http://127.0.0.1:8090',
            '-e','RPS=10','-e',f'DURATION_SECONDS={duration}',
            '-e','SUMMARY_FILE=/out/route-summary.json','/probe.js'],
            stdout=self.probe_log, stderr=subprocess.STDOUT)
        self.thread = threading.Thread(target=self.switches, daemon=True)
        self.thread.start()

    def switches(self):
        try:
            for slot, port in [('green',8082), ('blue',8080)]:
                if self.cancel.wait(60):
                    return
                # Python lockf and Java FileChannel share POSIX record locks on this Linux volume.
                result = self.control("""
import fcntl,json,os,signal,time,urllib.request
from pathlib import Path
p=Path('/deploy-state'); slot,port=__import__('sys').argv[1:]; before=time.monotonic()
with (p/'jobs.lock').open('a+') as lock:
 fcntl.lockf(lock,fcntl.LOCK_EX)
 acquired=time.monotonic()
 temp=p/'upstream.next'; temp.write_text('upstream app { server 127.0.0.1:'+port+'; }\\n'); temp.replace(p/'upstream.conf')
 os.kill(int((p/'nginx.pid').read_text()),signal.SIGHUP)
 for attempt in range(50):
  r=urllib.request.urlopen('http://127.0.0.1:8090/api/news?page=1&size=1',timeout=5)
  if r.headers['X-Daynomy-Slot']==slot: break
  time.sleep(.1)
 else: raise RuntimeError('Nginx did not switch')
 temp=p/'owner.next'; temp.write_text(slot+'\\n'); temp.replace(p/'active-slot')
print(json.dumps({'slot':slot,'lock_wait_seconds':round(acquired-before,3),'timestamp_ms':time.time_ns()//1000000}))
""", slot, str(port))
                self.transitions.append(json.loads(result))
                (self.output/'transitions.json').write_text(json.dumps(self.transitions,indent=2))
        except Exception as error:
            self.errors.append(type(error).__name__ + ': ' + str(error))

    def finish(self):
        self.thread.join(timeout=330)
        if self.thread.is_alive() or self.errors or len(self.transitions)!=2:
            raise RuntimeError('Ownership transitions failed: '+str(self.errors))
        if self.process.wait(timeout=30):
            raise RuntimeError('Nginx observer failed')
        summary=json.loads((self.output/'route-summary.json').read_text())
        rows=[json.loads(line) for line in (self.output/'route-samples.jsonl').read_text().splitlines()]
        slots=[]
        for row in rows:
            if row['slot'] not in ('blue','green'): raise RuntimeError('Unexpected route slot')
            if not slots or slots[-1]!=row['slot']: slots.append(row['slot'])
        if not summary['complete'] or summary['failures'] or slots!=['blue','green','blue']:
            raise RuntimeError('Nginx request continuity failed: '+str(slots))
        summary['observed_slots']=slots
        summary['scheduler_audit']=self.validate_audit()
        (self.output/'handoff-result.json').write_text(json.dumps(summary,indent=2))

    def validate_audit(self):
        from datetime import datetime
        records=[]
        logs={'blue':self.command('docker','logs',self.app),
              'green':self.command('docker','exec',self.app,'cat','/tmp/memory-green.log')}
        for slot,lines in logs.items():
            for line in lines.splitlines():
                try: row=json.loads(line)
                except ValueError: continue
                if row.get('message') not in ('deployment.scheduled.started','deployment.scheduled.finished'): continue
                timestamp=int(datetime.fromisoformat(row['@timestamp'].replace('Z','+00:00')).timestamp()*1000)
                records.append((timestamp,slot,row['message'].rsplit('.',1)[1]))
        counts={'blue':0,'green':0}
        running=None
        for timestamp,slot,event in sorted(records):
            expected='blue'
            for transition in self.transitions:
                if transition['timestamp_ms']<=timestamp: expected=transition['slot']
            near_flip=any(abs(timestamp-t['timestamp_ms'])<100 for t in self.transitions)
            if slot!=expected and not near_flip:
                raise RuntimeError('Inactive scheduler ran: '+str((timestamp,slot,event,expected)))
            if event=='started':
                if running is not None: raise RuntimeError('Scheduled jobs overlapped across JVMs')
                running=slot; counts[slot]+=1
            else:
                if running!=slot: raise RuntimeError('Scheduled audit lacks corresponding start')
                running=None
        if counts['blue']<2 or counts['green']<1:
            raise RuntimeError('No real guarded jobs observed on both slots: '+str(counts))
        return {'starts_by_slot':counts,'inactive_executions':0,'cross_jvm_overlaps':0}

    def cleanup(self):
        self.cancel.set()
        for name in (self.probe,self.nginx):
            subprocess.run(['docker','rm','-f',name],capture_output=True)
        if self.thread: self.thread.join(timeout=5)
        if self.probe_log: self.probe_log.close()
        subprocess.run(['docker','volume','rm','-f',self.volume],capture_output=True)
