#!/usr/bin/env python3
"""Single-EC2 blue/green deployment. Run as root after bootstrap; no schema changes."""

import argparse
import contextlib
import fcntl
import hashlib
import json
import os
import re
import shutil
import subprocess
import sys
import tempfile
import time
import urllib.request
import zipfile
from pathlib import Path

APP = Path('/home/ec2-user/backend')
STATE = APP / 'deployment'
ROUTE = Path('/etc/nginx/conf.d/daynomy-backend-upstream.conf')
SLOTS = {'blue': ('daynomy', 8080, 8181), 'green': ('daynomy-green', 8082, 8182)}
RESERVE = 256 * 1024 * 1024
ENVIRONMENT = 'production'
PUBLIC_ORIGIN = 'https://daynomy.com'
EXPECTED_INSTANCE = 'i-0c863ab4dc205e273'
SLICE = 'daynomy-backend.slice'
PROC_ROOT = Path('/proc')
CGROUP_ROOT = Path('/sys/fs/cgroup')


def configure_environment(environment):
    global ENVIRONMENT, STATE, ROUTE, SLOTS, PUBLIC_ORIGIN, EXPECTED_INSTANCE, SLICE
    if environment not in ('production', 'development'):
        raise RuntimeError('Unsupported deployment environment')
    ENVIRONMENT = environment
    development = environment == 'development'
    STATE = APP / ('deployment-dev' if development else 'deployment')
    ROUTE = Path('/etc/nginx/conf.d/' + ('daynomy-backend-dev-upstream.conf' if development
                                        else 'daynomy-backend-upstream.conf'))
    service = 'daynomy-dev' if development else 'daynomy'
    SLOTS = {'blue': (service, 8080, 8181), 'green': (service + '-green', 8082, 8182)}
    PUBLIC_ORIGIN = 'https://dev.daynomy.com' if development else 'https://daynomy.com'
    EXPECTED_INSTANCE = 'i-0f2e92d9c7bd8fe06' if development else 'i-0c863ab4dc205e273'
    SLICE = 'daynomy-backend-dev.slice' if development else 'daynomy-backend.slice'


def require_target_instance():
    actual = Path('/sys/devices/virtual/dmi/id/board_asset_tag').read_text().strip()
    if actual != EXPECTED_INSTANCE:
        raise RuntimeError('Deployment target instance does not match the selected environment')


def command(*args):
    return subprocess.check_output(args, text=True, stderr=subprocess.STDOUT).strip()


def is_active(service):
    return subprocess.run(['systemctl', 'is-active', '--quiet', service],
                          stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL).returncode == 0


def atomic(path, data, mode=0o644):
    fd, name = tempfile.mkstemp(prefix='.' + path.name + '-', dir=path.parent)
    try:
        with os.fdopen(fd, 'w') as stream:
            stream.write(data)
            stream.flush()
            os.fsync(stream.fileno())
        os.chmod(name, mode)
        os.replace(name, path)
    finally:
        if os.path.exists(name):
            os.unlink(name)


def route(slot):
    _, api, management = SLOTS[slot]
    return (f'upstream daynomy_backend {{ server 127.0.0.1:{api}; }}\n'
            f'upstream daynomy_management {{ server 127.0.0.1:{management}; }}\n'
            'server { listen 127.0.0.1:8081; server_name localhost;\n'
            ' location /actuator/ { proxy_pass http://daynomy_management; }\n'
            ' location / { return 404; }\n}\n')


def available():
    match = re.search(r'^MemAvailable:\s+(\d+) kB', Path('/proc/meminfo').read_text(), re.M)
    return int(match[1]) * 1024


def candidate_available_required():
    return RESERVE + (480 if ENVIRONMENT=='development' else 400) * 1024 * 1024


def reclaim_development_memory(verify):
    """Page out cold CI/management memory; never change limits or touch app/security cgroups."""
    if ENVIRONMENT != 'development' or available() >= RESERVE + 512 * 1024 * 1024:
        return []
    if os.geteuid() != 0:
        raise RuntimeError('Development memory reclaim requires root')
    require_target_instance()
    memory = {line.split(':')[0]: int(line.split()[1]) * 1024
              for line in (PROC_ROOT / 'meminfo').read_text().splitlines()
              if line.split(':')[0] in ('SwapTotal', 'SwapFree')}
    if memory.get('SwapTotal', 0) < 1024**3 - 4096 or memory.get('SwapFree', 0) < 512 * 1024**2:
        return []
    units = (
        'actions.runner.woowacourse-teams-2026-DAYNOMY.frontend-dev-arm64-server.service',
        'actions.runner.woowacourse-teams-2026-DAYNOMY.backend-dev-al2023.service',
        'codedeploy-agent.service', 'docker.service',
    )
    # Modern kernels support per-request swappiness; older kernels use the plain ABI.
    child = '''import errno,sys
from pathlib import Path
path=Path(sys.argv[1]);amount=sys.argv[2]
for payload in (amount+' swappiness=200',amount):
 try:
  path.write_text(payload+'\\n');print('requested');break
 except OSError as error:
  if error.errno==errno.EINVAL and 'swappiness' in payload:continue
  if error.errno==errno.EAGAIN:print('partial');break
  raise
'''
    samples = []
    for round_number in range(6):
        free_swap = re.search(r'^SwapFree:\s+(\d+) kB', (PROC_ROOT / 'meminfo').read_text(), re.M)
        if not free_swap or int(free_swap[1]) < 256 * 1024:
            return samples
        for unit in units:
            if available() >= RESERVE + 512 * 1024 * 1024:
                return samples
            try:
                cg = command('systemctl', 'show', unit, '-p', 'ControlGroup', '--value')
            except subprocess.CalledProcessError:
                continue
            if cg != '/system.slice/' + unit:
                continue
            root = CGROUP_ROOT / cg.lstrip('/')
            try:
                swap_max = (root / 'memory.swap.max').read_text().strip()
                if swap_max == '0' or not (root / 'memory.reclaim').exists():
                    continue
                processes = []
                for pid in (root / 'cgroup.procs').read_text().split():
                    try: processes.append((PROC_ROOT / pid / 'comm').read_text().strip())
                    except OSError: pass
                # Never perturb another frontend deployment or an agent hook.
                if 'frontend-dev' in unit and 'Runner.Worker' in processes:
                    continue
                if unit == 'codedeploy-agent.service' and any(name not in ('ruby', 'codedeploy-agen') for name in processes):
                    continue
                current = int((root / 'memory.current').read_text())
                budget = 32 * 1024**2 if unit in ('codedeploy-agent.service', 'docker.service') else 64 * 1024**2
                budget = min(budget, current - 16 * 1024**2)
                if budget < 4 * 1024**2:
                    continue
                before = available()
                result = subprocess.run([sys.executable, '-c', child, str(root / 'memory.reclaim'), str(budget)],
                                        capture_output=True, text=True, timeout=15)
                row = {'unit': unit, 'round': round_number, 'requested_mib': budget // 1024**2,
                       'exit': result.returncode, 'available_before_kib': before // 1024,
                       'available_after_kib': available() // 1024,
                       'swap_current_kib': int((root / 'memory.swap.current').read_text()) // 1024}
            except (OSError, subprocess.TimeoutExpired):
                continue  # Reclaim is best effort; the unchanged RAM gate decides.
            verify()
            samples.append(row)
            print(json.dumps({'development_memory_reclaim': row}), flush=True)
    return samples


def probe(slot):
    _, api, management = SLOTS[slot]
    for path in ('health', 'health/readiness'):
        with urllib.request.urlopen(f'http://127.0.0.1:{management}/actuator/{path}', timeout=3) as response:
            if json.load(response)['status'] != 'UP':
                raise RuntimeError('Candidate health/readiness is not UP')
    with urllib.request.urlopen(f'http://127.0.0.1:{api}/api/news?page=1&size=1', timeout=3) as response:
        if response.headers.get('X-Daynomy-Slot') != slot:
            raise RuntimeError('Candidate slot header mismatch')
        body = json.load(response)
        if not isinstance(body.get('items'), list):
            raise RuntimeError('Candidate news response mismatch')
    # Validate observability before sending public requests; a busy executor is valid.
    tasks_idle(slot)


def proxy_probe(slot):
    with urllib.request.urlopen(PUBLIC_ORIGIN + '/api/news?page=1&size=1', timeout=5) as response:
        if response.headers.get('X-Daynomy-Slot') != slot:
            raise RuntimeError('Public proxy did not reach expected slot')


def tasks_idle(slot):
    # Only query the slot's loopback management port, never the public proxy.
    management = SLOTS[slot][2]
    with urllib.request.urlopen(
            f'http://127.0.0.1:{management}/actuator/deploymenttasks', timeout=3) as response:
        status = json.load(response)
    if status.get('slot') != slot or not isinstance(status.get('executors'), dict):
        raise RuntimeError('Invalid background task status')
    executors = status['executors']
    required = {'portfolioAnalysisTaskExecutor', 'portfolioPriceHistoryTaskExecutor'}
    if not required.issubset(executors):
        raise RuntimeError('Required background executors are not observed')
    for counts in executors.values():
        if not isinstance(counts, dict) or any(
                type(counts.get(key)) is not int or counts[key] < 0
                for key in ('active', 'queued', 'pending')):
            raise RuntimeError('Invalid background task counts')
    return all(counts['active'] == 0 and counts['queued'] == 0 and counts['pending'] == 0
               for counts in executors.values())


def wait_tasks(slot, timeout=300):
    deadline = time.monotonic() + timeout
    while True:
        try:
            if tasks_idle(slot):
                return True
        except Exception:
            # Unknown status must preserve the JVM, not kill an unobserved task.
            pass
        if time.monotonic() >= deadline:
            return False
        time.sleep(0.5)


def migrations(path):
    with zipfile.ZipFile(path) as archive:
        if archive.testzip() is not None:
            raise RuntimeError('JAR checksum validation failed')
        if 'BOOT-INF/classes/org/grit/daynomy/deployment/DeploymentSchedulingConfig.class' not in archive.namelist():
            raise RuntimeError('JAR lacks deployment ownership support')
        if 'BOOT-INF/classes/org/grit/daynomy/deployment/DeploymentTasksEndpoint.class' not in archive.namelist():
            raise RuntimeError('JAR lacks background task drain support')
        return {name: hashlib.sha256(archive.read(name)).hexdigest()
                for name in archive.namelist() if name.startswith('BOOT-INF/classes/db/migration/') and name.endswith('.sql')}


@contextlib.contextmanager
def jobs_lock(timeout=300):
    with (STATE / 'jobs.lock').open('a+') as stream:
        deadline = time.monotonic() + timeout
        while True:
            try:
                # FileChannel.lock() uses POSIX record locks, NOT BSD flock().
                fcntl.lockf(stream, fcntl.LOCK_EX | fcntl.LOCK_NB)
                break
            except BlockingIOError:
                if time.monotonic() >= deadline:
                    raise RuntimeError('An active batch did not finish; deployment aborted')
                time.sleep(0.2)
        try:
            yield
        finally:
            fcntl.lockf(stream, fcntl.LOCK_UN)


def identity(pid):
    try:
        fields = Path(f'/proc/{pid}/stat').read_text().rsplit(')', 1)[1].split()
        return fields[19]
    except (OSError, IndexError):
        return None


def nginx_workers():
    master = command('systemctl', 'show', 'nginx', '-p', 'MainPID', '--value')
    result = {}
    for entry in Path('/proc').iterdir():
        if not entry.name.isdigit():
            continue
        try:
            fields = (entry / 'stat').read_text().rsplit(')', 1)[1].split()
            if fields[1] == master and b'nginx: worker process' in (entry / 'cmdline').read_bytes():
                result[entry.name] = fields[19]
        except (OSError, IndexError):
            continue
    return result


def wait_workers(workers, timeout=120):
    deadline = time.monotonic() + timeout
    while any(identity(pid) == start for pid, start in workers.items()):
        if time.monotonic() >= deadline:
            return False
        time.sleep(0.5)
    return True


def score(slot, value):
    unit = SLOTS[slot][0]
    pid = command('systemctl', 'show', unit, '-p', 'MainPID', '--value')
    if pid != '0':
        Path(f'/proc/{pid}/oom_score_adj').write_text(str(value))


def candidate_override(slot, enabled):
    directory = Path('/etc/systemd/system') / (SLOTS[slot][0] + '.service.d')
    directory.mkdir(exist_ok=True)
    file = directory / 'candidate.conf'
    if enabled:
        atomic(file, '[Service]\nExecCondition=\nOOMScoreAdjust=500\n')
    else:
        file.unlink(missing_ok=True)
    command('systemctl', 'daemon-reload')


def deploy(artifact, version):
    if not re.fullmatch(r'[a-f0-9]{40}(?:-bg[0-9]+)?', version):
        raise RuntimeError('Invalid immutable release version')
    old = (STATE / 'active-slot').read_text().strip()
    if old not in SLOTS:
        raise RuntimeError('Unknown active slot')
    new = 'green' if old == 'blue' else 'blue'
    old_jar = (STATE / (old + '.jar')).resolve(strict=True)
    artifact = artifact.resolve(strict=True)
    if migrations(old_jar) != migrations(artifact):
        raise RuntimeError('Schema migrations differ; stage a reviewed compatible migration separately')
    probe(old)
    pending = STATE / 'retirement.json'
    if pending.exists():
        record = json.loads(pending.read_text())
        if (not wait_workers(record['workers'], timeout=0)
                or not wait_tasks(record['slot'], timeout=0)):
            raise RuntimeError('Previous requests or background tasks are still draining')
        command('systemctl', 'stop', SLOTS[record['slot']][0])
        candidate_override(record['slot'], False)
        pending.unlink()
    if is_active(SLOTS[new][0]):
        raise RuntimeError('Inactive slot unexpectedly runs; refusing to interrupt it')
    if available() < candidate_available_required():
        reclaim_development_memory(lambda: probe(old))
    if available() < candidate_available_required():
        raise RuntimeError('Insufficient available memory to start a candidate safely')
    release = APP / 'releases' / ('daynomy-' + version + '.jar')
    if release.exists():
        if hashlib.sha256(release.read_bytes()).digest() != hashlib.sha256(artifact.read_bytes()).digest():
            raise RuntimeError('Immutable release version already has different bytes')
    elif artifact != release:
        shutil.copyfile(artifact, release)
        os.chmod(release, 0o644)
    link = STATE / (new + '.jar')
    temporary = STATE / ('.' + new + '.jar')
    temporary.unlink(missing_ok=True)
    temporary.symlink_to(release)
    os.replace(temporary, link)
    previous_route = ROUTE.read_text()
    route_written = False
    workers = {}
    candidate_override(new, True)
    try:
        command('systemctl', 'start', SLOTS[new][0])
        for attempt in range(90):
            if available() < RESERVE:
                raise RuntimeError('Memory reserve breached during candidate warmup')
            try:
                probe(new)
                break
            except Exception:
                if attempt == 89:
                    raise RuntimeError('Candidate did not become ready')
                time.sleep(1)
        with jobs_lock():
            probe(new)
            if available() < RESERVE:
                raise RuntimeError('Memory reserve breached before switching')
            workers = nginx_workers()
            atomic(ROUTE, route(new))
            route_written = True
            command('nginx', '-t')
            command('systemctl', 'reload', 'nginx')
            for attempt in range(10):
                try:
                    proxy_probe(new)
                    break
                except Exception:
                    if attempt == 9:
                        raise
                    time.sleep(0.5)
            atomic(STATE / 'active-slot', new + '\n')
        for _ in range(15):
            probe(new)
            if available() < RESERVE:
                raise RuntimeError('Memory reserve breached during verification')
            time.sleep(1)
    except Exception:
        # Restore HTTP first; a long candidate batch must not delay HTTP recovery.
        rollback_workers = {}
        if route_written:
            rollback_workers = nginx_workers()
            atomic(ROUTE, previous_route)
            command('nginx', '-t')
            # Reload even if the original reload returned an error after taking effect.
            command('systemctl', 'reload', 'nginx')
            proxy_probe(old)
            with jobs_lock():
                atomic(STATE / 'active-slot', old + '\n')
        if wait_workers(rollback_workers) and wait_tasks(new):
            command('systemctl', 'stop', SLOTS[new][0])
            candidate_override(new, False)
        else:
            # Requests sent to the candidate before rollback must also drain safely.
            atomic(pending, json.dumps({'slot': new, 'workers': rollback_workers}))
        probe(old)
        raise
    # Requests accepted by old Nginx workers must finish before the old JVM stops.
    score(new, -500)
    score(old, 500)
    retired = False
    if wait_workers(workers) and wait_tasks(old):
        try:
            command('systemctl', 'stop', SLOTS[old][0])
            retired = True
        except subprocess.CalledProcessError:
            pass  # Keep the healthy new slot serving; retry retirement on the next deployment.
    if not retired:
        atomic(pending, json.dumps({'slot': old, 'workers': workers}))
    command('systemctl', 'enable', SLOTS['blue'][0], SLOTS['green'][0])
    candidate_override(new, False)
    atomic(STATE / 'last-deployment.json', json.dumps({
        'active': new, 'previous': old, 'release': str(release), 'previous_release': str(old_jar),
        'retirement_pending': pending.exists(), 'timestamp_ms': time.time_ns() // 1000000,
    }, indent=2))
    print(json.dumps({'active': new, 'version': version, 'retirement_pending': pending.exists()}))


def owns_slot(slot):
    return (STATE/'active-slot').read_text().strip()==slot


def prepare_slot(slot):
    # An interrupted deployment may have written the HTTP route before committing
    # scheduler ownership. The durable ownership record determines boot recovery.
    if owns_slot(slot):
        previous=ROUTE.read_text()
        atomic(ROUTE,route(slot))
        try:
            command('nginx','-t')
            command('systemctl','reload','nginx')
        except Exception:
            atomic(ROUTE,previous)
            raise


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--environment', choices=('development', 'production'), default='production')
    parser.add_argument('--artifact', type=Path)
    parser.add_argument('--version')
    parser.add_argument('--owns-slot', choices=SLOTS)
    parser.add_argument('--prepare-slot', choices=SLOTS)
    args = parser.parse_args()
    if os.geteuid() != 0:
        parser.error('Run as root')
    configure_environment(args.environment)
    require_target_instance()
    if args.owns_slot:
        raise SystemExit(0 if owns_slot(args.owns_slot) else 1)
    if args.prepare_slot:
        prepare_slot(args.prepare_slot)
        return
    if args.artifact is None or args.version is None:
        parser.error('--artifact and --version are required for deployment')
    with (STATE / 'deploy.lock').open('a+') as stream:
        fcntl.flock(stream, fcntl.LOCK_EX | fcntl.LOCK_NB)
        deploy(args.artifact, args.version)


if __name__ == '__main__':
    main()
