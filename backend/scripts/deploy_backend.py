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
        directory = os.open(path.parent, os.O_RDONLY | os.O_DIRECTORY)
        try:
            os.fsync(directory)
        finally:
            os.close(directory)
    finally:
        if os.path.exists(name):
            os.unlink(name)


def route(slot):
    _, api, management = SLOTS[slot]
    return (f'upstream daynomy_backend {{ server 127.0.0.1:{api}; }}\n'
            f'upstream daynomy_management {{ server 127.0.0.1:{management}; }}\n'
            'server { listen 127.0.0.1:8081; server_name localhost;\n'
            ' location /actuator/ { proxy_set_header Host localhost; proxy_pass http://daynomy_management; }\n'
            ' location / { return 404; }\n}\n')


def available():
    match = re.search(r'^MemAvailable:\s+(\d+) kB', Path('/proc/meminfo').read_text(), re.M)
    return int(match[1]) * 1024


def candidate_available_required():
    return RESERVE + 480 * 1024 * 1024


def reclaim_development_memory(verify):
    # Preserve the development-only API used by its explicit memory preparation tool.
    if ENVIRONMENT != 'development':
        return []
    return reclaim_management_memory(verify)


def memory_reclaim_units():
    environment = 'dev' if ENVIRONMENT == 'development' else 'prod'
    units = (
        f'actions.runner.woowacourse-teams-2026-DAYNOMY.frontend-{environment}-arm64-server.service',
        f'actions.runner.woowacourse-teams-2026-DAYNOMY.backend-{environment}-al2023.service',
        'codedeploy-agent.service',
    )
    return units + (('docker.service',) if ENVIRONMENT == 'development' else ())


def reclaim_management_memory(verify):
    """Page out cold CI/management memory; never change limits or touch app/security cgroups."""
    if available() >= RESERVE + 512 * 1024 * 1024:
        return []
    if os.geteuid() != 0:
        raise RuntimeError('Management memory reclaim requires root')
    require_target_instance()
    memory = {line.split(':')[0]: int(line.split()[1]) * 1024
              for line in (PROC_ROOT / 'meminfo').read_text().splitlines()
              if line.split(':')[0] in ('SwapTotal', 'SwapFree')}
    if memory.get('SwapTotal', 0) < 1024**3 - 4096 or memory.get('SwapFree', 0) < 512 * 1024**2:
        return []
    units = memory_reclaim_units()
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
                # Production excludes every busy runner. Keep the existing development
                # backend self-reclaim path; another frontend deployment stays protected.
                busy_runner = 'actions.runner.' in unit and 'Runner.Worker' in processes
                if busy_runner and (ENVIRONMENT == 'production' or 'frontend-dev' in unit):
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
            print(json.dumps({'management_memory_reclaim': row}), flush=True)
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
    task_status(slot)


def proxy_probe(slot):
    with urllib.request.urlopen(PUBLIC_ORIGIN + '/api/news?page=1&size=1', timeout=5) as response:
        if response.headers.get('X-Daynomy-Slot') != slot:
            raise RuntimeError('Public proxy did not reach expected slot')


def task_status(slot):
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
    scheduled = status.get('scheduled')
    if scheduled is not None and (not isinstance(scheduled, dict)
            or scheduled.get('protocol') != 1 or type(scheduled.get('active')) is not int
            or scheduled['active'] < 0):
        raise RuntimeError('Invalid scheduled task status')
    return status


def tasks_idle(slot, legacy_idle=False):
    status = task_status(slot)
    scheduled = status.get('scheduled')
    if scheduled is None and not legacy_idle:
        raise RuntimeError('Scheduled task tracking is required before retirement')
    return ((scheduled is None or scheduled['active'] == 0)
            and all(counts['active'] == 0 and counts['queued'] == 0 and counts['pending'] == 0
                    for counts in status['executors'].values()))


def wait_tasks(slot, timeout=300, legacy_idle=False):
    deadline = time.monotonic() + timeout
    while True:
        try:
            if tasks_idle(slot, legacy_idle=legacy_idle):
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


def candidate_override(slot, enabled, retain_priority=False):
    directory = Path('/etc/systemd/system') / (SLOTS[slot][0] + '.service.d')
    directory.mkdir(exist_ok=True)
    file = directory / 'candidate.conf'
    if enabled:
        atomic(file, '[Service]\nExecCondition=\nOOMScoreAdjust=500\nRestart=no\n')
    elif retain_priority:
        # Restore the boot ownership condition. While the old JVM is retained,
        # rollback owns failure recovery; automatic restart can race that handoff.
        atomic(file, '[Service]\nOOMScoreAdjust=500\nRestart=no\n')
    else:
        file.unlink(missing_ok=True)
    command('systemctl', 'daemon-reload')


def retirement_record(slot, active, workers, legacy_idle=False, inactive_candidate=False):
    pid = command('systemctl', 'show', SLOTS[slot][0], '-p', 'MainPID', '--value')
    if not pid.isdigit():
        raise RuntimeError('Invalid retiring JVM PID')
    release = (STATE / (slot + '.jar')).resolve(strict=True)
    start = identity(pid) if pid != '0' else None
    if pid != '0' and start is None:
        raise RuntimeError('Retiring JVM identity could not be observed')
    return {'protocol': 1, 'slot': slot, 'active': active, 'workers': workers,
            'pid': pid, 'start': start,
            'release': str(release), 'sha256': digest(release),
            'legacy_idle': legacy_idle, 'inactive_candidate': inactive_candidate,
            'phase': 'draining', 'created_ms': time.time_ns() // 1000000}


def save_retirement(record):
    atomic(STATE / 'retirement.json', json.dumps(record, indent=2))


def digest(path):
    value = hashlib.sha256()
    with path.open('rb') as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b''):
            value.update(block)
    return value.hexdigest()


def restore_http(slot):
    atomic(ROUTE, route(slot))
    command('nginx', '-t')
    command('systemctl', 'reload', 'nginx')
    for attempt in range(10):
        try:
            proxy_probe(slot)
            break
        except Exception:
            if attempt == 9:
                raise
            time.sleep(.5)
    # Never wait for the batch lock to restore a healthy HTTP path.
    atomic(STATE / 'active-slot', slot + '\n')


def retire_pending():
    """Called under deploy.lock; no batch deadline is allowed to kill a busy JVM."""
    path = STATE / 'retirement.json'
    if not path.exists():
        return True
    record = json.loads(path.read_text())
    slot, active = record.get('slot'), record.get('active')
    if (record.get('protocol') != 1 or slot not in SLOTS or active not in SLOTS
            or slot == active or not isinstance(record.get('workers'), dict)):
        raise RuntimeError('Retirement record needs review; background tasks are still draining')
    if (STATE / 'active-slot').read_text().strip() != active:
        raise RuntimeError('Refusing to retire the active slot or an uncommitted deployment')
    if str((STATE / (slot + '.jar')).resolve(strict=True)) != record['release']:
        raise RuntimeError('Retiring release changed; manual review required')
    if digest(Path(record['release'])) != record['sha256']:
        raise RuntimeError('Retiring release bytes changed; refusing to stop it')
    pid = command('systemctl', 'show', SLOTS[slot][0], '-p', 'MainPID', '--value')
    if pid == '0':
        if record.get('failed_active_exit'):
            # A dead candidate cannot drain work. Preserve its failure separately;
            # do not report successful drain or adopt an automatically restarted PID.
            probe(active)
            if not wait_workers(record['workers'], timeout=0):
                return False
        elif not record.get('drain_confirmed') and not record.get('inactive_candidate'):
            record['phase'] = 'unexpected_exit'
            save_retirement(record)
            raise RuntimeError('Retiring JVM exited before work completion was observed')
    else:
        if pid != record['pid'] or identity(pid) != record['start']:
            raise RuntimeError('Retiring JVM identity changed; refusing to stop it')
        try:
            probe(active)
        except Exception:
            # Rollback remains possible while the old JVM is deliberately retained.
            probe(slot)
            workers = nginx_workers()
            replacement = retirement_record(active, slot, workers)
            if replacement['pid'] == '0':
                replacement['failed_active_exit'] = True
            restore_http(slot)
            save_retirement(replacement)
            atomic(STATE / 'last-retirement.json', json.dumps({
                **record, 'outcome': 'http_rolled_back', 'timestamp_ms': time.time_ns() // 1000000}))
            return False
        if not wait_workers(record['workers'], timeout=0):
            return False
        try:
            status = task_status(slot)
        except Exception:
            return False
        if status.get('scheduled') is None and not record.get('legacy_idle'):
            # Compatibility with the previous JAR: after ownership changed, one
            # successful nonblocking lock proves its previous batch has finished.
            try:
                with jobs_lock(timeout=0):
                    record['legacy_idle'] = True
                    save_retirement(record)
            except RuntimeError:
                return False
        try:
            idle = wait_tasks(slot, timeout=0, legacy_idle=record.get('legacy_idle', False))
        except Exception:
            return False
        if not idle:
            return False
        # Persist the proof before stopping, so a controller crash after stop is recoverable.
        record['drain_confirmed'] = True
        record['phase'] = 'stop_requested'
        save_retirement(record)
        command('systemctl', 'stop', SLOTS[slot][0])
        if command('systemctl', 'show', SLOTS[slot][0], '-p', 'MainPID', '--value') != '0':
            return False
    candidate_override(slot, False)
    candidate_override(active, False)
    failed = bool(record.get('failed_active_exit'))
    atomic(STATE / 'last-retirement.json', json.dumps({
        **record, 'outcome': 'failed_active_exited' if failed else 'drained',
        'timestamp_ms': time.time_ns() // 1000000}, indent=2))
    path.unlink()
    score(active, -500)
    print(json.dumps({'retired': slot, 'active': active, 'work_drained': not failed}), flush=True)
    return True


def deploy(artifact, version, verify_existing_release=False):
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
    timer = 'daynomy-dev-retirement.timer' if ENVIRONMENT == 'development' else 'daynomy-retirement.timer'
    if not is_active(timer):
        raise RuntimeError('Install and enable the retirement timer before deployment')
    pending = STATE / 'retirement.json'
    if pending.exists() and not retire_pending():
        raise RuntimeError('Previous requests or background tasks are still draining')
    if is_active(SLOTS[new][0]):
        raise RuntimeError('Inactive slot unexpectedly runs; refusing to interrupt it')
    release = APP / 'releases' / ('daynomy-' + version + '.jar')
    if verify_existing_release:
        if ENVIRONMENT != 'production':
            raise RuntimeError('Existing-release verification is production-only')
        def checksum(path):
            digest = hashlib.sha256()
            with path.open('rb') as stream:
                for chunk in iter(lambda: stream.read(1024 * 1024), b''):
                    digest.update(chunk)
            return digest.hexdigest()
        current_checksum = checksum(old_jar)
        if current_checksum == checksum(artifact):
            if release.exists() and checksum(release) != current_checksum:
                raise RuntimeError('Immutable release version already has different bytes')
            if ((STATE / 'active-slot').read_text().strip() != old
                    or ROUTE.read_text() != route(old)
                    or command('systemctl', 'show', SLOTS[new][0], '-p', 'MainPID', '--value') != '0'):
                raise RuntimeError('Existing release routing or inactive slot is inconsistent')
            if available() < RESERVE:
                raise RuntimeError('Existing release memory reserve breached')
            probe(old)
            proxy_probe(old)
            atomic(STATE / 'last-verification.json', json.dumps({
                'active': old, 'version': version, 'sha256': current_checksum,
                'unchanged': True, 'timestamp_ms': time.time_ns() // 1000000,
            }, indent=2))
            print(json.dumps({'active': old, 'version': version, 'unchanged': True}), flush=True)
            return
    if available() < candidate_available_required():
        reclaim_management_memory(lambda: probe(old))
    if available() < candidate_available_required():
        raise RuntimeError('Insufficient available memory to start a candidate safely')
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
        probe(new)
        if available() < RESERVE:
            raise RuntimeError('Memory reserve breached before switching')
        workers = nginx_workers()
        record = retirement_record(old, new, workers)
        record['phase'] = 'prepared'
        save_retirement(record)
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
        # Running work keeps its POSIX lock and finishes in the old JVM. Only
        # future work changes owner; HTTP must not wait for the long batch.
        atomic(STATE / 'active-slot', new + '\n')
        record['phase'] = 'draining'
        save_retirement(record)
        for _ in range(15):
            probe(new)
            if available() < RESERVE:
                raise RuntimeError('Memory reserve breached during verification')
            time.sleep(1)
    except Exception:
        rollback_workers = nginx_workers() if route_written else {}
        if route_written:
            restore_http(old)
        elif pending.exists():
            pending.unlink()
        cleanup = retirement_record(new, old, rollback_workers,
                                    legacy_idle=not route_written,
                                    inactive_candidate=not route_written)
        save_retirement(cleanup)
        try:
            retire_pending()
        except Exception as cleanup_error:
            print(json.dumps({'candidate_cleanup_pending': type(cleanup_error).__name__}), flush=True)
        probe(old)
        raise
    # Keep the original batch protected until it has actually drained.
    candidate_override(new, False, retain_priority=True)
    retired = retire_pending()
    if (STATE / 'active-slot').read_text().strip() != new:
        raise RuntimeError('New version failed during drain; HTTP was rolled back')
    command('systemctl', 'enable', SLOTS['blue'][0], SLOTS['green'][0])
    atomic(STATE / 'last-deployment.json', json.dumps({
        'active': new, 'previous': old, 'release': str(release), 'previous_release': str(old_jar),
        'retirement_pending': not retired, 'timestamp_ms': time.time_ns() // 1000000,
    }, indent=2))
    print(json.dumps({'active': new, 'version': version, 'retirement_pending': not retired}))


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
    parser.add_argument('--retire-pending', action='store_true')
    parser.add_argument('--verify-existing-release', action='store_true',
                        help='Verify an identical live production JAR without another cutover')
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
    if args.retire_pending:
        with (STATE / 'deploy.lock').open('a+') as stream:
            try:
                fcntl.flock(stream, fcntl.LOCK_EX | fcntl.LOCK_NB)
            except BlockingIOError:
                return  # A deployment owns the state; retry on the next timer tick.
            retire_pending()
        return
    if args.artifact is None or args.version is None:
        parser.error('--artifact and --version are required for deployment')
    with (STATE / 'deploy.lock').open('a+') as stream:
        fcntl.flock(stream, fcntl.LOCK_EX | fcntl.LOCK_NB)
        deploy(args.artifact, args.version, verify_existing_release=args.verify_existing_release)


if __name__ == '__main__':
    main()
