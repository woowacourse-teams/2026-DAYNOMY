#!/usr/bin/env python3
"""One-time single-host migration; deliberately uses one measured restart.

Run the preflight first. Applying requires the previous service to be idle and an
external availability observer already running. Existing release and env stay intact.
"""
import argparse
import json
import os
import re
import shutil
import tempfile
import time
from pathlib import Path
import deploy_backend as deployment

SYSTEMD = Path('/etc/systemd/system')
SITE = Path('/etc/nginx/conf.d/daynomy-site.conf')
ALLOY = Path('/etc/alloy/config.alloy')
BOOT_HELPER = Path('/usr/local/libexec/daynomy/deploy_backend.py')


def unit(slot):
    _, api, management = deployment.SLOTS[slot]
    return f'''[Unit]
Description=DAYNOMY backend {slot}
After=network-online.target nginx.service
Wants=network-online.target nginx.service
[Service]
User=ec2-user
WorkingDirectory={deployment.APP}
EnvironmentFile={deployment.APP}/daynomy.env
Environment=MALLOC_ARENA_MAX=2
ExecCondition=/usr/bin/python3 {BOOT_HELPER} --owns-slot {slot}
ExecStartPre=/usr/bin/python3 {BOOT_HELPER} --prepare-slot {slot}
ExecStart=/usr/lib/jvm/java-21-amazon-corretto.aarch64/bin/java -Xmx128m -XX:TieredStopAtLevel=1 -XX:+ExitOnOutOfMemoryError -Ddaynomy.deployment.slot={slot} -Ddaynomy.deployment.directory={deployment.STATE} -jar {deployment.STATE}/{slot}.jar --server.address=127.0.0.1 --server.port={api} --management.server.address=127.0.0.1 --management.server.port={management} --server.shutdown=graceful --spring.lifecycle.timeout-per-shutdown-phase=120s --spring.task.scheduling.shutdown.await-termination=true --spring.task.scheduling.shutdown.await-termination-period=120s
Restart=on-failure
RestartSec=5
TimeoutStopSec=150
KillSignal=SIGTERM
Slice=daynomy-backend.slice
OOMScoreAdjust=-500
SyslogIdentifier=daynomy
[Install]
WantedBy=multi-user.target
'''


def idle_original_jobs():
    pid=deployment.command('systemctl','show','daynomy','-p','MainPID','--value')
    dump=deployment.command('timeout','15','/usr/lib/jvm/java-21-amazon-corretto.aarch64/bin/java',
                            '-Xmx32m','-XX:TieredStopAtLevel=1','-m',
                            'jdk.jcmd/sun.tools.jcmd.JCmd',pid,'Thread.print')
    schedulers=[block for block in re.split(r'\n(?=")',dump)
                if block.startswith(('"scheduling-','"email-delivery-'))]
    if not schedulers or any('ScheduledThreadPoolExecutor$DelayedWorkQueue.take' not in block
                             for block in schedulers):
        raise RuntimeError('Original scheduled jobs are not confirmed idle; retry outside a batch window')


def preflight(artifact):
    if os.geteuid()!=0: raise RuntimeError('Run as root')
    if BOOT_HELPER.exists(): raise RuntimeError('Boot recovery helper already exists; review before replacing')
    if deployment.STATE.exists(): raise RuntimeError('Already bootstrapped or unfinished state; inspect backup instead of rerunning')
    old=(deployment.APP/'current.jar').resolve(strict=True)
    if deployment.is_active('daynomy-green'): raise RuntimeError('Green already runs')
    if not deployment.is_active('daynomy'): raise RuntimeError('Original service is not running')
    environment_file=deployment.APP/'daynomy.env'
    protected={'JAVA_TOOL_OPTIONS','JDK_JAVA_OPTIONS','_JAVA_OPTIONS','MALLOC_ARENA_MAX'}
    for line in environment_file.read_text().splitlines():
        key,separator,value=line.partition('=')
        if separator and key.strip() in protected and value.strip():
            raise RuntimeError('Environment file overrides a reviewed JVM/malloc setting; inspect locally')
    site=SITE.read_text()
    if site.count('proxy_pass http://127.0.0.1:8080;')!=4:
        raise RuntimeError('Nginx differs from reviewed four API routes')
    if deployment.ROUTE.exists(): raise RuntimeError('Target Nginx include already exists')
    if any(SYSTEMD.glob('daynomy.service.d/*.conf')): raise RuntimeError('Unreviewed systemd overrides')
    # Exact migration hashes; do not require the old JAR to contain the new guard.
    import zipfile,hashlib
    with zipfile.ZipFile(old) as archive:
        before={n:hashlib.sha256(archive.read(n)).hexdigest() for n in archive.namelist()
                if n.startswith('BOOT-INF/classes/db/migration/') and n.endswith('.sql')}
    if before!=deployment.migrations(artifact): raise RuntimeError('Schema migration mismatch')
    if deployment.available()<deployment.RESERVE+150*1024*1024:
        raise RuntimeError('Insufficient memory reserve for controlled restart')
    if ALLOY.read_text().count('matches = "_SYSTEMD_UNIT=daynomy.service"')!=1:
        raise RuntimeError('Alloy differs from reviewed backend journal selector')
    if deployment.command('systemctl','show','alloy','-p','CanReload','--value')!='yes':
        raise RuntimeError('Alloy does not support a reload; review monitoring migration first')
    with tempfile.NamedTemporaryFile(mode='w',suffix='.alloy') as candidate:
        candidate.write(ALLOY.read_text().replace('matches = "_SYSTEMD_UNIT=daynomy.service"',
                                                  'matches = "SYSLOG_IDENTIFIER=daynomy"'))
        candidate.flush()
        deployment.command('alloy','validate',candidate.name)
    deployment.command('nginx','-t')
    return old,site


def apply(artifact,expected_sha256):
    import hashlib
    old,site=preflight(artifact)
    if hashlib.sha256(old.read_bytes()).hexdigest()!=expected_sha256:
        raise RuntimeError('Original production artifact changed')
    idle_original_jobs()
    backup=deployment.APP/('bootstrap-backup-'+str(time.time_ns()))
    backup.mkdir(mode=0o700)
    for source in [SYSTEMD/'daynomy.service',SITE,ALLOY]: shutil.copy2(source,backup/source.name)
    (backup/'original-release').write_text(str(old))
    deployment.STATE.mkdir(mode=0o755)
    shutil.chown(deployment.STATE,user='ec2-user',group='ec2-user')
    for name,data in [('active-slot','blue\n'),('jobs.lock','')]:
        deployment.atomic(deployment.STATE/name,data)
        shutil.chown(deployment.STATE/name,user='ec2-user',group='ec2-user')
    (deployment.STATE/'blue.jar').symlink_to(artifact)
    BOOT_HELPER.parent.mkdir(parents=True,exist_ok=True)
    deployment.atomic(BOOT_HELPER,Path(__file__).with_name('deploy_backend.py').read_text(),mode=0o755)
    try:
        # The unmodified original cannot participate in the ownership lock.
        deployment.command('systemctl','stop','daynomy')
        deployment.atomic(SYSTEMD/'daynomy.service',unit('blue'))
        deployment.atomic(SYSTEMD/'daynomy-green.service',unit('green'))
        deployment.atomic(SYSTEMD/'daynomy-backend.slice','[Unit]\nDescription=Combined backend JVM memory budget\n[Slice]\nMemoryMax=800M\nMemorySwapMax=0\n')
        deployment.atomic(SITE,site.replace('proxy_pass http://127.0.0.1:8080;','proxy_pass http://daynomy_backend;'))
        deployment.atomic(deployment.ROUTE,deployment.route('blue'))
        deployment.command('nginx','-t')
        updated_alloy=ALLOY.read_text().replace('matches = "_SYSTEMD_UNIT=daynomy.service"','matches = "SYSLOG_IDENTIFIER=daynomy"')
        deployment.atomic(ALLOY,updated_alloy)
        deployment.command('alloy','validate',str(ALLOY))
        deployment.command('systemctl','reload','alloy')
        deployment.command('systemctl','daemon-reload')
        # Old management port 8081 must be free before Nginx binds its stable proxy.
        deployment.command('systemctl','reload','nginx')
        deployment.command('systemctl','start','daynomy')
        for attempt in range(90):
            try:
                deployment.probe('blue'); deployment.proxy_probe('blue')
                break
            except Exception:
                if attempt==89: raise
                time.sleep(1)
        if deployment.available()<deployment.RESERVE: raise RuntimeError('Post-restart memory reserve breached')
        deployment.command('systemctl','enable','daynomy','daynomy-green')
        print(json.dumps({'bootstrapped':True,'backup':str(backup),'active':'blue'}))
    except BaseException as failure:
        recovery_errors=[]
        def recover(*args):
            try: deployment.command(*args)
            except Exception: recovery_errors.append(' '.join(args[:3]))
        recover('systemctl','stop','daynomy')
        shutil.copy2(backup/'daynomy.service',SYSTEMD/'daynomy.service')
        shutil.copy2(backup/SITE.name,SITE)
        shutil.copy2(backup/ALLOY.name,ALLOY)
        recover('systemctl','reload','alloy')
        deployment.ROUTE.unlink(missing_ok=True)
        for name in ('daynomy-green.service','daynomy-backend.slice'):
            (SYSTEMD/name).unlink(missing_ok=True)
        recover('nginx','-t')
        recover('systemctl','reload','nginx')
        # Wait for old nginx workers to release the listening management socket.
        for _ in range(50):
            import socket
            with socket.socket() as sock:
                if sock.connect_ex(('127.0.0.1',8081))!=0: break
            time.sleep(.1)
        recover('systemctl','daemon-reload')
        BOOT_HELPER.unlink(missing_ok=True)
        recover('systemctl','start','daynomy')
        if recovery_errors:
            raise RuntimeError('Bootstrap failed; inspect recovery commands: '+', '.join(recovery_errors)) from failure
        raise


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--artifact',required=True,type=Path)
    parser.add_argument('--apply',action='store_true')
    parser.add_argument('--expected-current-sha256')
    args=parser.parse_args()
    artifact=args.artifact.resolve(strict=True)
    if args.apply:
        if not args.expected_current_sha256 or not re.fullmatch('[a-f0-9]{64}',args.expected_current_sha256):
            parser.error('--apply requires --expected-current-sha256')
        apply(artifact,args.expected_current_sha256)
    else:
        old,_=preflight(artifact)
        print(json.dumps({'preflight':'passed','current':str(old),'next_step':'observe baseline then apply'}))


if __name__=='__main__': main()
