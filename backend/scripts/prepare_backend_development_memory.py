#!/usr/bin/env python3
"""Bounded development-only swap/JVM experiment; production is always rejected."""
import argparse
import fcntl
import hashlib
import http.cookiejar
import json
import os
import re
import shutil
import subprocess
import time
import urllib.request
from pathlib import Path

import bootstrap_backend
import deploy_backend

BASE = Path('/var/lib/daynomy-dev-memory-943')
UNIT = Path('/etc/systemd/system/daynomy-dev.service')
SWAP = Path('/swapfile-daynomy-dev')
FSTAB = Path('/etc/fstab')
SYSCTL = Path('/etc/sysctl.d/90-daynomy-dev-memory.conf')
MARKER = '# DAYNOMY issue 943 bounded development memory trial'
FLAGS = '-Xmx128m -XX:TieredStopAtLevel=1 -XX:+ExitOnOutOfMemoryError'


def run(*args, check=True, timeout=180):
    result = subprocess.run(args, capture_output=True, text=True, timeout=timeout)
    if check and result.returncode:
        raise RuntimeError('Command failed: ' + ' '.join(args[:3]))
    return result.stdout.strip()


def emit(value):
    print(json.dumps(value), flush=True)


def tuned_unit(original):
    if MARKER in original:
        return original
    if original.count('[Service]\n') != 1 or re.search(r'^Memory(?:Max|SwapMax)=|^Environment=.*MALLOC_ARENA_MAX', original, re.M):
        raise RuntimeError('Unreviewed original service memory configuration')
    pattern = r'^ExecStart=(/[^ \n]+/java) -jar /home/ec2-user/backend/current.jar(?= |$)'
    updated, count = re.subn(pattern, lambda m: 'ExecStart=' + m[1] + ' ' + FLAGS + ' -jar /home/ec2-user/backend/current.jar', original, flags=re.M)
    if count != 1:
        raise RuntimeError('Unreviewed original Java command')
    return updated.replace('[Service]\n', '[Service]\n' + MARKER + '\nEnvironment=MALLOC_ARENA_MAX=2\nMemoryMax=450M\nMemorySwapMax=0\n')


def snapshot():
    memory = {line.split(':')[0]: int(line.split()[1]) for line in Path('/proc/meminfo').read_text().splitlines() if line.split(':')[0] in ('MemAvailable', 'SwapTotal', 'SwapFree')}
    pid = run('systemctl', 'show', 'daynomy-dev', '-p', 'MainPID', '--value')
    if not pid.isdigit() or pid == '0':
        raise RuntimeError('Development Java is not running')
    text = Path('/proc/' + pid + '/status').read_text()
    memory.update({key: int(match[1]) if (match := re.search(r'^' + key + r':\s+(\d+)', text, re.M)) else 0 for key in ('VmRSS', 'VmSwap')})
    memory['restarts'] = int(run('systemctl', 'show', 'daynomy-dev', '-p', 'NRestarts', '--value'))
    memory['at_ms'] = int(time.time() * 1000)
    return memory


def health():
    with urllib.request.urlopen('http://127.0.0.1:8081/actuator/health', timeout=5) as response:
        if json.load(response).get('status') != 'UP':
            raise RuntimeError('Development health is not UP')


def wait_healthy():
    for attempt in range(90):
        try:
            health()
            return
        except Exception:
            if attempt == 89:
                raise RuntimeError('Development did not become healthy') from None
            time.sleep(2)


def ensure_swap():
    existing = Path('/proc/swaps').read_text().splitlines()[1:]
    if existing:
        emit({'swap_action': 'preserve_existing_swap'})
        return
    if SWAP.exists():
        raise RuntimeError('Refusing to overwrite an existing inactive swap file')
    if str(SWAP) in FSTAB.read_text():
        raise RuntimeError('Unreviewed existing swap persistence entry')
    if shutil.disk_usage('/').free < 2 * 1024 ** 3:
        raise RuntimeError('Need 1GiB swap plus at least 1GiB disk reserve')
    if SYSCTL.exists():
        raise RuntimeError('Unreviewed swap configuration already exists')
    temporary = SWAP.with_name(SWAP.name + '.creating')
    fd = os.open(temporary, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    os.close(fd)
    activated = False
    try:
        run('dd', 'if=/dev/zero', 'of=' + str(temporary), 'bs=1M', 'count=1024', 'status=none')
        run('mkswap', str(temporary))
        os.replace(temporary, SWAP)
        run('swapon', str(SWAP))
        activated = True
        original = FSTAB.read_text()
        shutil.copy2(FSTAB, BASE / 'fstab.before')
        deploy_backend.atomic(FSTAB, original.rstrip() + '\n' + str(SWAP) + ' none swap sw 0 0\n')
        deploy_backend.atomic(SYSCTL, 'vm.swappiness=10\n')
        run('sysctl', '-w', 'vm.swappiness=10')
        emit({'swap_action': 'created_1GiB', 'backend_swap_limit': 0, 'swappiness': 10})
    except BaseException:
        temporary.unlink(missing_ok=True)
        # Never delete an active swap file or force swapoff under pressure.
        if not activated:
            SWAP.unlink(missing_ok=True)
        raise


def restore_unit():
    original = (BASE / 'service.before').read_text()
    current = UNIT.read_text()
    if current not in (original, tuned_unit(original)):
        raise RuntimeError('Service changed outside this experiment; refusing overwrite')
    run('systemctl', 'stop', 'daynomy-dev', check=False)
    deploy_backend.atomic(UNIT, original)
    run('systemctl', 'daemon-reload')
    run('systemctl', 'reset-failed', 'daynomy-dev', check=False)
    run('systemctl', 'start', 'daynomy-dev')
    wait_healthy()
    emit({'service_restored': True, 'swap_preserved_for_safe_recovery': True})


def apply(expected_sha, seconds):
    artifact = Path('/home/ec2-user/backend/current.jar').resolve(strict=True)
    if hashlib.sha256(artifact.read_bytes()).hexdigest() != expected_sha:
        raise RuntimeError('Deployed JAR changed; inspect latest dev before applying')
    if Path('/home/ec2-user/backend/deployment-dev').exists():
        raise RuntimeError('Already bootstrapped; use the deployment controller')
    if run('systemctl', 'show', 'daynomy-dev', '-p', 'DropInPaths', '--value'):
        raise RuntimeError('Unreviewed service overrides')
    original = UNIT.read_text()
    if MARKER in original and (not (BASE / 'service.before').exists() or original != tuned_unit((BASE / 'service.before').read_text())):
        raise RuntimeError('Marked service differs from the owned experiment configuration')
    updated = tuned_unit(original)
    protected = {'JAVA_TOOL_OPTIONS', 'JDK_JAVA_OPTIONS', '_JAVA_OPTIONS', 'MALLOC_ARENA_MAX'}
    for line in Path('/home/ec2-user/backend/daynomy.env').read_text().splitlines():
        key, separator, value = line.partition('=')
        if separator and key.strip() in protected and value.strip():
            raise RuntimeError('Environment overrides a reviewed JVM/malloc setting')
    emit({'phase': 'before', 'sample': snapshot()})
    ensure_swap()
    if updated != original:
        if (BASE / 'service.before').exists():
            raise RuntimeError('Previous experiment backup exists; inspect before reapplying')
        bootstrap_backend.configure_environment('development')
        bootstrap_backend.idle_original_jobs()
        shutil.copy2(UNIT, BASE / 'service.before')
        try:
            # Apply MemoryMax only after stopping; never shrink the running 640MiB JVM.
            run('systemctl', 'stop', 'daynomy-dev')
            deploy_backend.atomic(UNIT, updated)
            run('systemctl', 'daemon-reload')
            run('systemctl', 'reset-failed', 'daynomy-dev', check=False)
            run('systemctl', 'start', 'daynomy-dev')
            wait_healthy()
        except BaseException:
            restore_unit()
            raise
    elif not (BASE / 'service.before').exists():
        raise RuntimeError('Marked service has no owned recovery backup')
    deploy_backend.configure_environment('development')
    rows = []
    durations = []
    client = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))
    until = time.monotonic() + seconds
    try:
        deploy_backend.reclaim_development_memory(health)
        while time.monotonic() < until:
            health()
            started = time.monotonic()
            for path in ('/api/news?page=1&size=1', '/api/portfolio/analysis/tasks'):
                with client.open('http://127.0.0.1:8080' + path, timeout=10) as response:
                    data = json.load(response)
                    if response.status != 200 or (path.endswith('/tasks') and data.get('status') != 'EMPTY'):
                        raise RuntimeError('Development smoke response mismatch')
            durations.append(time.monotonic() - started)
            row = snapshot()
            rows.append(row)
            emit({'phase': 'observing', 'sample': row})
            if row['restarts']:
                raise RuntimeError('Bounded JVM restarted during observation')
            time.sleep(5)
    except BaseException:
        restore_unit()
        raise
    (BASE / 'samples.json').write_text(json.dumps(rows))
    available = sorted(row['MemAvailable'] for row in rows)
    emit({'phase': 'completed', 'samples': len(rows), 'duration_seconds': seconds,
          'min_available_kib': min(available), 'p10_available_kib': available[len(available)//10],
          'max_java_rss_kib': max(row['VmRSS'] for row in rows),
          'max_java_swap_kib': max(row['VmSwap'] for row in rows),
          'paired_smoke_p95_seconds': sorted(durations)[int((len(durations)-1)*.95)],
          'candidate_memory_gate_passed': available[len(available)//10] >= 656 * 1024})


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--apply', action='store_true')
    parser.add_argument('--restore', action='store_true')
    parser.add_argument('--expected-current-sha256')
    parser.add_argument('--observe-seconds', type=int, default=600)
    args = parser.parse_args()
    if Path('/sys/devices/virtual/dmi/id/board_asset_tag').read_text().strip() != 'i-0f2e92d9c7bd8fe06':
        raise RuntimeError('Refusing non-development instance')
    if os.geteuid() != 0:
        raise RuntimeError('Run as root')
    if not 120 <= args.observe_seconds <= 1800:
        raise RuntimeError('Observation must be 120..1800 seconds')
    if not args.apply and not args.restore:
        emit({'plan': '1GiB swap for host, backend swap disabled, heap128m/C1/arena2, MemoryMax450M', 'sample': snapshot()})
        return
    BASE.mkdir(mode=0o700, exist_ok=True)
    with (BASE / 'operations.lock').open('w') as lock:
        fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        if args.restore:
            restore_unit()
        else:
            if not re.fullmatch(r'[a-f0-9]{64}', args.expected_current_sha256 or ''):
                raise RuntimeError('An inspected current JAR SHA256 is required')
            apply(args.expected_current_sha256, args.observe_seconds)


if __name__ == '__main__':
    main()
