#!/usr/bin/env python3
"""Explicit development-only bootstrap/switch of the already verified running JAR."""
import argparse
import contextlib
import fcntl
import hashlib
import json
import os
import re
import subprocess
import threading
import time
import urllib.request
from pathlib import Path

import bootstrap_backend
import deploy_backend


def transition_sample():
    row={'at_ms':int(time.time()*1000),'jvms':{}}
    row['available_kib']=int(re.search(r'^MemAvailable:\s+(\d+)',Path('/proc/meminfo').read_text(),re.M)[1])
    for slot,(unit,_,_) in deploy_backend.SLOTS.items():
        pid=subprocess.check_output(['systemctl','show',unit,'-p','MainPID','--value'],text=True,timeout=3).strip()
        if not pid.isdigit():raise RuntimeError('Unexpected JVM PID')
        if pid=='0':continue
        try:status=Path('/proc/'+pid+'/status').read_text()
        except FileNotFoundError:continue  # A retiring JVM may exit between reads.
        row['jvms'][slot]={key:int(match[1]) for key in ('VmRSS','VmSwap')
                           if (match:=re.search(r'^'+key+r':\s+(\d+)',status,re.M))}
    cg=subprocess.check_output(['systemctl','show',deploy_backend.SLICE,'-p','ControlGroup','--value'],text=True,timeout=3).strip()
    if cg.startswith('/') and '..' not in Path(cg).parts:
        root=Path('/sys/fs/cgroup')/cg.lstrip('/')
        for name in ('memory.current','memory.peak','memory.max','memory.swap.current','memory.swap.max'):
            try:row[name]=int((root/name).read_text().strip())
            except (FileNotFoundError,ValueError):pass
        try:row['memory.events']={key:int(value) for key,value in (line.split() for line in (root/'memory.events').read_text().splitlines())}
        except FileNotFoundError:pass
    return row


@contextlib.contextmanager
def observe_transition():
    stopped=threading.Event()
    def sample():
        try:print(json.dumps({'development_transition_sample':transition_sample()}),flush=True)
        except Exception as error:print(json.dumps({'development_transition_observation_error':type(error).__name__}),flush=True)
    def observe():
        while not stopped.wait(2):sample()
    sample()
    observer=threading.Thread(target=observe,daemon=True)
    observer.start()
    try:yield
    finally:
        stopped.set()
        observer.join(timeout=12)
        sample()


def current_artifact(app):
    state = app / 'deployment-dev'
    if state.exists():
        slot = (state / 'active-slot').read_text().strip()
        if slot not in ('blue', 'green'):
            raise RuntimeError('Unknown active development slot')
        return (state / (slot + '.jar')).resolve(strict=True)
    return (app / 'current.jar').resolve(strict=True)


def repair_proxy():
    slot=(deploy_backend.STATE/'active-slot').read_text().strip()
    if slot not in deploy_backend.SLOTS:
        raise RuntimeError('Unknown active development slot')
    deploy_backend.probe(slot)
    previous=deploy_backend.ROUTE.read_text()
    try:
        deploy_backend.prepare_slot(slot)
        # Reload signals Nginx asynchronously; the previous worker can answer briefly.
        for attempt in range(10):
            try:
                with urllib.request.urlopen('http://127.0.0.1:8081/actuator/health',timeout=5) as response:
                    if json.load(response).get('status')!='UP':
                        raise RuntimeError('Stable management proxy is not UP')
                deploy_backend.proxy_probe(slot)
                break
            except Exception:
                if attempt==9:raise
                time.sleep(.5)
        # Keep boot recovery from reinstalling the obsolete proxy configuration.
        deploy_backend.atomic(bootstrap_backend.BOOT_HELPER,Path(deploy_backend.__file__).read_text(),mode=0o755)
    except Exception:
        deploy_backend.atomic(deploy_backend.ROUTE,previous)
        deploy_backend.command('nginx','-t')
        deploy_backend.command('systemctl','reload','nginx')
        raise
    print(json.dumps({'development_proxy_repaired':True,'active':slot,'probe_attempts':attempt+1}),flush=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--operation', choices=('bootstrap', 'switch', 'repair-proxy', 'install-retirement'), required=True)
    parser.add_argument('--expected-current-sha256', required=True)
    parser.add_argument('--version')
    parser.add_argument('--apply', action='store_true')
    args = parser.parse_args()
    if os.geteuid() != 0:
        raise RuntimeError('Run as root')
    bootstrap_backend.configure_environment('development')
    deploy_backend.require_target_instance()
    if not re.fullmatch(r'[a-f0-9]{64}', args.expected_current_sha256):
        raise RuntimeError('An inspected current JAR SHA256 is required')
    artifact = current_artifact(deploy_backend.APP)
    checksum = hashlib.sha256(artifact.read_bytes()).hexdigest()
    if checksum != args.expected_current_sha256:
        raise RuntimeError('Active JAR changed; inspect latest development state')
    print(json.dumps({'operation': args.operation, 'artifact': artifact.name, 'sha256': checksum}), flush=True)
    if not args.apply:
        if args.operation == 'bootstrap':
            bootstrap_backend.preflight(artifact)
        return
    memory_state = Path('/var/lib/daynomy-dev-memory-943')
    memory_state.mkdir(mode=0o700, exist_ok=True)
    with (memory_state / 'operations.lock').open('a+') as operations:
        fcntl.flock(operations, fcntl.LOCK_EX | fcntl.LOCK_NB)
        with observe_transition():
            if args.operation == 'bootstrap':
                bootstrap_backend.apply(artifact, checksum, reuse_initialized_development_release=True)
            else:
                with (deploy_backend.STATE / 'deploy.lock').open('a+') as deployment:
                    fcntl.flock(deployment, fcntl.LOCK_EX | fcntl.LOCK_NB)
                    if args.operation=='repair-proxy':
                        repair_proxy()
                    elif args.operation=='install-retirement':
                        bootstrap_backend.install_retirement_timer()
                        print(json.dumps({'retirement_timer_installed':True}),flush=True)
                    else:
                        deploy_backend.deploy(artifact, args.version or '')


if __name__ == '__main__':
    main()
