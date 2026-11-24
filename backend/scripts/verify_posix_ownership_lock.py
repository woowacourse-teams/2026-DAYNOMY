"""Verify Python lockf / Java FileChannel compatibility on Linux, without AWS."""
import argparse
import json
import subprocess
import time
from pathlib import Path


def run(*args):
    return subprocess.check_output(args,text=True,stderr=subprocess.STDOUT).strip()


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--build',type=Path,required=True)
    args=parser.parse_args()
    build=args.build.resolve(strict=True)
    name='daynomy-posix-check-'+str(time.time_ns())
    volume=name+'-state'
    try:
        run('docker','volume','create',volume)
        run('docker','run','-d','--name',name+'-holder','--network','none','-v',volume+':/state',
            'python:3.13-slim','python','-c',"""
import fcntl,time
from pathlib import Path
p=Path('/state'); (p/'active-slot').write_text('blue')
with (p/'jobs.lock').open('a+') as f:
 fcntl.lockf(f,fcntl.LOCK_EX); (p/'locked').touch()
 while not (p/'release').exists():time.sleep(.05)
 (p/'active-slot').write_text('green')
""")
        def exists(file):
            return subprocess.run(['docker','exec',name+'-holder','test','-f','/state/'+file],capture_output=True).returncode==0
        for _ in range(50):
            if exists('locked'):break
            time.sleep(.1)
        else:raise RuntimeError('Holder failed to acquire lock')
        run('docker','run','-d','--name',name+'-waiter','--network','none',
            '-v',volume+':/state','-v',str(build/'classes/java/main')+':/main:ro',
            '-v',str(build/'classes/java/test')+':/test:ro',
            'daynomy-memory-corretto:21.0.13-prod','java','-Xmx32m','-cp','/main:/test',
            'org.grit.daynomy.deployment.DeploymentOwnershipTest$GateChild','/state')
        for _ in range(50):
            if 'READY' in run('docker','logs',name+'-waiter'):break
            time.sleep(.1)
        else:raise RuntimeError('Java waiter did not start')
        time.sleep(.3)
        if exists('ran'):raise RuntimeError('Java bypassed the Python lock')
        run('docker','exec',name+'-holder','touch','/state/release')
        if run('docker','wait',name+'-waiter')!='0':raise RuntimeError('Java waiter failed')
        value=run('docker','run','--rm','--network','none','-v',volume+':/state',
                  'python:3.13-slim','python','-c',"from pathlib import Path; print(Path('/state/ran').read_text())")
        if value!='green':raise RuntimeError('Java read ownership before acquiring lock')
        print(json.dumps({'linux_posix_lock_compatible':True,'blocked_until_release':True,'owner_read_after_lock':'green'}))
    finally:
        for suffix in ('-holder','-waiter'):
            subprocess.run(['docker','rm','-f',name+suffix],capture_output=True)
        subprocess.run(['docker','volume','rm','-f',volume],capture_output=True)

if __name__=='__main__':main()
