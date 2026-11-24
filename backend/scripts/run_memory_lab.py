"""Compare heap limits locally with an isolated DB and no external network."""

import argparse
import base64
import hashlib
import json
import os
import re
import subprocess
import threading
import time
from pathlib import Path

from observe_deployment_memory import sample


def command(*args, **kwargs):
    return subprocess.run(args, check=True, capture_output=True, text=True, **kwargs).stdout.strip()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--ownership-handoff', action='store_true', help='Verify guarded batches and real Nginx switch/rollback')
    parser.add_argument('--jar', required=True, type=Path)
    parser.add_argument('--output', required=True, type=Path)
    parser.add_argument('--sha', required=True)
    parser.add_argument('--duration', type=int, default=60)
    parser.add_argument('--overlap-duration', type=int, default=30)
    parser.add_argument('--java-image', default='eclipse-temurin:21-jre-jammy')
    parser.add_argument('--workload', choices=['news', 'portfolio'], default='news')
    parser.add_argument('--pair-memory-mib', type=int,
                        help='Optional combined cgroup budget for two JVMs; no swap')
    parser.add_argument('--cases', nargs='+', choices=['default', 'heap256', 'heap192', 'heap192serial',
                                                     'heap256arena2', 'heap192arena2', 'heap128arena2',
                                                     'heap192arena2c1', 'heap128arena2c1', 'heap96arena2c1'],
                        default=['default', 'heap256', 'heap192'])
    args = parser.parse_args()
    if args.ownership_handoff and (args.workload != 'portfolio' or not args.pair_memory_mib or args.overlap_duration < 240 or len(args.cases) != 1):
        parser.error('ownership-handoff requires portfolio, one case, pair-memory-mib and overlap-duration >=240')
    jar = args.jar.resolve(strict=True)
    output = args.output.resolve()
    output.mkdir(parents=True, exist_ok=False)
    if not 10 <= args.duration <= 3600 or not 10 <= args.overlap_duration <= 3600:
        parser.error('duration and overlap-duration must be 10..3600 seconds')
    if args.pair_memory_mib is not None and not 384 <= args.pair_memory_mib <= 1846:
        parser.error('pair-memory-mib must be 384..1846')
    prefix = f'daynomy-memory-{time.time_ns()}'
    database = prefix + '-db'
    app = prefix + '-app'
    generator = prefix + '-k6'
    volume = prefix + '-state'
    nginx = prefix + '-nginx'
    from ownership_handoff_lab import HandoffLab
    handoff = HandoffLab(command, prefix, app, nginx, volume, output) if args.ownership_handoff else None
    loadtest = Path(__file__).resolve().parents[1] / 'loadtest'
    probe = loadtest / ('memory-portfolio.js' if args.workload == 'portfolio' else 'deployment-availability.js')
    flags = ['-XX:MaxRAM=1846m', '-XX:ActiveProcessorCount=2']
    disabled = [
        'news.generation.economy', 'news.generation.korea', 'news.generation.us',
        'stock.us.calendar.sync', 'stock.us.sync', 'stock.calendar.sync',
        'stock.sync', 'stock.price.sync', 'exchange-rate.sync',
        'portfolio.snapshot', 'portfolio.delayed-quotes', 'league.calculation',
        'person.post-collection', 'content.collection', 'investment-calendar.sync',
        'email.auto-analysis', 'email.delivery',
    ]
    settings = [f'--{key}.enabled=false' for key in disabled]
    settings += ['--email.delivery.scheduling-enabled=false',
                 '--management.server.address=0.0.0.0']
    if args.workload == 'portfolio':
        settings.remove('--portfolio.snapshot.enabled=false')
        settings += ['--portfolio.snapshot.enabled=true', '--portfolio.snapshot.cron=*/30 * * * * *',
                     '--logging.pattern.console=%d{ISO8601} %level %kvp %msg%n']
    if handoff:
        settings += ['--logging.level.org.grit.daynomy.deployment=DEBUG']
    environment = {
        'SPRING_PROFILES_ACTIVE': 'prod',
        'DB_URL': f'jdbc:postgresql://{database}:5432/memory_lab',
        'DB_USERNAME': 'lab', 'DB_PASSWORD': 'memory-lab-only',
        'GOOGLE_CLIENT_ID': 'lab', 'GOOGLE_CLIENT_SECRET': 'lab',
        'JWT_SECRET': base64.b64encode(b'0123456789abcdef0123456789abcdef').decode('ascii'),
        'AWS_REGION': 'ap-northeast-2', 'AWS_S3_BUCKET': 'memory-lab',
        'AWS_S3_PUBLIC_BASE_URL': 'http://127.0.0.1/unused',
        'AWS_EC2_METADATA_DISABLED': 'true', 'PUBLIC_DATA_SERVICE_KEY': 'lab',
    }
    manifest = {'source_sha': args.sha, 'jar': str(jar),
                'rebuilt_jar_sha256': hashlib.sha256(jar.read_bytes()).hexdigest(),
                'network': 'internal',
                'data': '1000 synthetic published news rows', 'duration_seconds': args.duration,
                'rps': 10, 'warmup_seconds': 30,
                'note': 'Local screening only; no production data, batches or external API flows.'}
    if args.workload == 'portfolio':
        manifest.update(workload='portfolio', data='50 members, 30 KR assets each, 7500 history rows, 60 synthetic price dates',
                        batch='real PortfolioSnapshotScheduler every 30 seconds',
                        note='Local mixed API and snapshot batch screening; other batches and external services are excluded.')
    manifest['images'] = {
        image: command('docker', 'image', 'inspect', '--format', '{{.Id}}', image)
        for image in ('postgres:16-alpine', args.java_image, 'grafana/k6:latest')
    }
    manifest['overlap_duration_seconds'] = args.overlap_duration
    manifest['java_runtime'] = command('docker', 'run', '--rm', '--network', 'none',
                                     args.java_image, 'sh', '-c', 'java -version 2>&1; rpm -q glibc 2>/dev/null || true')
    manifest['combined_pair_memory_limit_mib'] = args.pair_memory_mib
    manifest['ownership_handoff'] = args.ownership_handoff
    (output / 'manifest.json').write_text(json.dumps(manifest, indent=2))
    results = []
    try:
        command('docker', 'network', 'create', '--internal', prefix)
        if handoff:
            handoff.initialize()
        command('docker', 'run', '-d', '--name', database, '--network', prefix,
                '--memory', '256m', '-e', 'POSTGRES_USER=lab',
                '-e', 'POSTGRES_PASSWORD=memory-lab-only', '-e', 'POSTGRES_DB=memory_lab',
                'postgres:16-alpine')
        for _ in range(60):
            ready = subprocess.run(['docker', 'exec', database, 'pg_isready', '-U', 'lab'],
                                   capture_output=True).returncode == 0
            if ready:
                break
            time.sleep(1)
        else:
            raise RuntimeError('Lab database did not become ready')
        heaps = {'default': [], 'heap256': ['-Xmx256m'], 'heap192': ['-Xmx192m'],
                 'heap192serial': ['-Xmx192m', '-XX:+UseSerialGC'],
                 'heap256arena2': ['-Xmx256m'], 'heap192arena2': ['-Xmx192m'],
                 'heap128arena2': ['-Xmx128m'],
                 'heap192arena2c1': ['-Xmx192m', '-XX:TieredStopAtLevel=1'],
                 'heap128arena2c1': ['-Xmx128m', '-XX:TieredStopAtLevel=1'],
                 'heap96arena2c1': ['-Xmx96m', '-XX:TieredStopAtLevel=1']}
        for case in args.cases:
            heap = heaps[case]
            folder = output / case
            folder.mkdir()
            environment.pop('MALLOC_ARENA_MAX', None)
            if 'arena2' in case:
                environment['MALLOC_ARENA_MAX'] = '2'
            dbname = 'memory_lab'
            if args.workload == 'portfolio':
                dbname = f'memory_lab_{case}'
                command('docker', 'exec', database, 'createdb', '-U', 'lab', dbname)
                environment['DB_URL'] = f'jdbc:postgresql://{database}:5432/{dbname}'
            env_args = [value for key, val in environment.items() for value in ('-e', f'{key}={val}')]
            command('docker', 'run', '-d', '--name', app, '--network', prefix,
                    '--memory', f'{args.pair_memory_mib or 1846}m',
                    '--memory-swap', f'{args.pair_memory_mib or 1846}m', '--cpus', '2',
                    '-v', f'{jar}:/app/app.jar:ro', *(['-v', volume + ':/deploy-state'] if handoff else []), *env_args,
                    args.java_image, 'java', *flags, *heap, *(['-Ddaynomy.deployment.slot=blue', '-Ddaynomy.deployment.directory=/deploy-state'] if handoff else []),
                    '-jar', '/app/app.jar', *settings)
            api = f'http://{app}:8080'

            def fetch(path):
                return command('docker', 'exec', app, 'curl', '--fail', '--silent',
                               '--max-time', '5', 'http://127.0.0.1:8081' + path)
            started = time.monotonic()
            rows = []
            phase = 'startup'
            stop = threading.Event()

            def observe():
                with (folder / 'memory.jsonl').open('w') as stream:
                    while not stop.is_set():
                        try:
                            status = command('docker', 'exec', app, 'cat', '/proc/1/status')
                            try:
                                metrics = fetch('/actuator/prometheus')
                            except Exception:
                                metrics = ''
                            # Host memory belongs to the Docker VM and is not a production estimate.
                            meminfo = command('docker', 'exec', app, 'cat', '/proc/meminfo')
                            row = sample(metrics, meminfo, status)
                            row['phase'] = phase
                            row['cgroup_current_mib'] = round(int(command('docker','exec',app,'cat',
                                '/sys/fs/cgroup/memory.current')) / 1048576, 2)
                            row['cgroup_peak_mib'] = round(int(command('docker','exec',app,'cat',
                                '/sys/fs/cgroup/memory.peak')) / 1048576, 2)
                            events = command('docker','exec',app,'cat','/sys/fs/cgroup/memory.events')
                            row['oom_kill'] = int(re.search(r'^oom_kill (\d+)',events,re.M)[1])
                            row['memory_max_events'] = int(re.search(r'^max (\d+)',events,re.M)[1])
                            rows.append(row)
                            stream.write(json.dumps(row) + '\n')
                            stream.flush()
                        except Exception:
                            pass  # Actuator is absent during early startup; readiness handles failure.
                        stop.wait(2)

            observer = threading.Thread(target=observe, daemon=True)
            observer.start()
            try:
                for _ in range(90):
                    try:
                        if json.loads(fetch('/actuator/health'))['status'] == 'UP':
                            break
                    except Exception:
                        pass
                    time.sleep(1)
                else:
                    raise RuntimeError(f'{case}: readiness failed; see local app.log')
                startup = round(time.monotonic() - started, 2)
                # This database exists only on the internal lab network.
                seed = (loadtest / 'sql/seed-memory-lab.sql').read_text() if args.workload == 'portfolio' else """
INSERT INTO news(title,content,sources,image_source,category,status,published_at,created_at,updated_at)
SELECT 'Memory lab ' || n, repeat('synthetic content ', 100), '[]'::jsonb, '{}'::jsonb,
       'STOCK', 'PUBLISHED', now(), now(), now()
FROM generate_series(1,1000) n
WHERE NOT EXISTS (SELECT 1 FROM news);
"""
                command('docker', 'exec', '-i', database, 'psql', '-U', 'lab', '-d', dbname,
                        '-v', 'ON_ERROR_STOP=1', input=seed)
                for label, duration in [('warmup', 30), ('measure', args.duration)]:
                    phase = label
                    run = subprocess.run([
                        'docker', 'run', '--rm', '--name', generator,
                        '--network', prefix, '--user', str(os.getuid()),
                        '-v', f'{probe}:/probe.js:ro', '-v', f'{folder}:/out',
                        'grafana/k6:latest', 'run', '--quiet', '--log-format', 'raw', '--console-output',
                        f'/out/{label}-samples.jsonl',
                        '-e', f'BASE_URL={api}', '-e', 'RPS=10',
                        '-e', f'DURATION_SECONDS={duration}',
                        '-e', f'SUMMARY_FILE=/out/{label}-summary.json', '/probe.js',
                    ], capture_output=True, text=True)
                    (folder / f'{label}-k6.log').write_text(run.stdout + run.stderr)
                    if run.returncode:
                        raise RuntimeError(f'{case}: incomplete k6 run')
                    summary = json.loads((folder / f'{label}-summary.json').read_text())
                    if not summary['complete'] or summary['failures']:
                        raise RuntimeError(f'{case}: requests failed; inspect summary')
                measured = json.loads((folder / 'measure-summary.json').read_text())
                integrity = None
                if args.workload == 'portfolio':
                    for _ in range(60):
                        logs = command('docker', 'logs', app)
                        completed = re.findall(r'snapshotCount["\s:=]+(\d+)', logs)
                        if any(int(count) > 0 for count in completed):
                            break
                        time.sleep(1)
                    else:
                        raise RuntimeError('No nonempty snapshot batch completion observed')
                    if any(int(n) > 0 for n in re.findall(r'failureCount["\s:=]+(\d+)', logs)):
                        raise RuntimeError('Snapshot batch reported failures')
                    verification_sql = """
SELECT json_build_object('snapshots',(SELECT count(*) FROM portfolio_daily_snapshots),
 'quantity_mismatches',(SELECT count(*) FROM portfolio_holdings h WHERE h.quantity <> 100 +
   (SELECT sum(x.transaction_quantity) FROM portfolio_holding_histories x
    WHERE x.portfolio_id=h.portfolio_id AND x.asset_id=h.asset_id AND x.change_type='BUY')),
 'purchase_mismatches',(SELECT count(*) FROM portfolio_holdings WHERE average_purchase_price<>10000),
 'snapshot_value_mismatches',(SELECT count(*) FROM portfolio_daily_snapshots s WHERE s.total_evaluation_amount <>
   (SELECT sum(100+coalesce((SELECT sum(x.transaction_quantity) FROM portfolio_holding_histories x
     WHERE x.portfolio_id=h.portfolio_id AND x.asset_id=h.asset_id AND x.change_type='BUY'
       AND x.effective_date<=s.base_date),0))*11000 FROM portfolio_holdings h WHERE h.portfolio_id=s.portfolio_id)));
"""
                    verification = command('docker', 'exec', database, 'psql', '-U', 'lab', '-d', dbname,
                        '-At', '-v', 'ON_ERROR_STOP=1', '-c', verification_sql)
                    integrity = json.loads(verification)
                    (folder / 'integrity.json').write_text(json.dumps(integrity, indent=2))
                    if integrity != {'snapshots':3000,'quantity_mismatches':0,'purchase_mismatches':0,'snapshot_value_mismatches':0}:
                        raise RuntimeError('Lab ledger/snapshot integrity mismatch; see integrity.json')
                    accepted = sum(
                        1 for path in folder.glob('*-samples.jsonl')
                        for line in path.read_text().splitlines()
                        if (record := json.loads(line)).get('operation') == 'trade' and record['ok']
                    )
                    buy_count = int(command('docker','exec',database,'psql','-U','lab','-d',dbname,
                                            '-At','-c',"SELECT count(*) FROM portfolio_holding_histories WHERE change_type='BUY'"))
                    trades = {'accepted_http_trades':accepted, 'seed_buy_histories':6000,
                              'actual_buy_histories':buy_count, 'consistent':buy_count==6000+accepted}
                    (folder/'trade-integrity.json').write_text(json.dumps(trades,indent=2))
                    if not trades['consistent']:
                        raise RuntimeError('HTTP successes and persisted trade count differ')
                if not rows:
                    raise RuntimeError('No memory samples collected')
                result = {'case': case, 'jvm_flags': flags + heap, 'startup_seconds': startup,
                          'peak_observed_rss_mib': max(r['process_rss_mib'] for r in rows),
                          'last_rss_mib': rows[-1]['process_rss_mib'],
                          'peak_observed_heap_mib': max(r['heap_used_mib'] for r in rows if r['heap_used_mib'] is not None),
                          'gc_pause_count_delta': (rows[-1]['gc_pause_count'] or 0) - (rows[0]['gc_pause_count'] or 0),
                          'http': measured, 'integrity': integrity,
                          'malloc_arena_max': environment.get('MALLOC_ARENA_MAX')}
                results.append(result)
                (output / 'results.json').write_text(json.dumps(results, indent=2))
                print(json.dumps(result), flush=True)
                if args.pair_memory_mib:
                    phase = 'green-startup'
                    green_settings = [s for s in settings if not s.startswith('--portfolio.snapshot.enabled=')]
                    green_settings += [f'--portfolio.snapshot.enabled={str(bool(handoff)).lower()}', '--server.port=8082',
                                       '--management.server.port=8083']
                    command('docker', 'exec', '-d', app, 'sh', '-c',
                            'exec java "$@" >/tmp/memory-green.log 2>&1', 'sh',
                            *flags, *heap, *(['-Ddaynomy.deployment.slot=green', '-Ddaynomy.deployment.directory=/deploy-state'] if handoff else []), '-jar', '/app/app.jar', *green_settings)
                    pair = {'combined_limit_mib': args.pair_memory_mib, 'ready': False, 'validated': False,
                            'oom_kill': 0, 'peak_cgroup_mib': None}
                    try:
                        for _ in range(90):
                            events = command('docker', 'exec', app, 'cat', '/sys/fs/cgroup/memory.events')
                            pair['oom_kill'] = int(re.search(r'^oom_kill (\d+)', events, re.M)[1])
                            pair['peak_cgroup_mib'] = round(int(command('docker','exec',app,'cat',
                                                        '/sys/fs/cgroup/memory.peak'))/1048576, 2)
                            if pair['oom_kill']:
                                break
                            try:
                                green = command('docker','exec',app,'curl','-fsS','--max-time','3',
                                                'http://127.0.0.1:8083/actuator/health')
                                if json.loads(green)['status']=='UP':
                                    pair['ready'] = True
                                    break
                            except Exception:
                                pass
                            time.sleep(1)
                        if pair['ready'] and not pair['oom_kill']:
                            phase = 'overlap'
                            if handoff:
                                handoff.start(args.overlap_duration)
                            overlap = subprocess.run([
                                'docker','run','--rm','--name',generator,'--network',prefix,
                                '--user',str(os.getuid()),'-v',f'{probe}:/probe.js:ro',
                                '-v',f'{folder}:/out','grafana/k6:latest','run','--quiet',
                                '--log-format','raw','--console-output','/out/overlap-samples.jsonl',
                                '-e',f'BASE_URL={api}',
                                '-e',f'SECONDARY_BASE_URL=http://{app}:8082' if args.workload=='portfolio' else 'SECONDARY_BASE_URL=',
                                '-e','RPS=10','-e',f'DURATION_SECONDS={args.overlap_duration}',
                                '-e','SUMMARY_FILE=/out/overlap-summary.json','/probe.js',
                            ],capture_output=True,text=True)
                            (folder/'overlap-k6.log').write_text(overlap.stdout+overlap.stderr)
                            if handoff:
                                handoff.finish()
                            summary=json.loads((folder/'overlap-summary.json').read_text())
                            events=command('docker','exec',app,'cat','/sys/fs/cgroup/memory.events')
                            pair['memory_events'] = dict((key, int(value)) for key, value in
                                                         (line.split() for line in events.splitlines()))
                            pair['memory_pressure'] = command('docker','exec',app,'cat',
                                                              '/sys/fs/cgroup/memory.pressure')
                            pair['oom_kill']=int(re.search(r'^oom_kill (\d+)',events,re.M)[1])
                            pair['peak_cgroup_mib']=round(int(command('docker','exec',app,'cat',
                                                       '/sys/fs/cgroup/memory.peak'))/1048576,2)
                            pair['validated']=overlap.returncode==0 and summary['complete'] and summary['failures']==0 and pair['oom_kill']==0
                            green = command('docker','exec',app,'curl','-fsS','--max-time','3',
                                            'http://127.0.0.1:8083/actuator/health')
                            pair['green_still_ready'] = json.loads(green)['status']=='UP'
                            pair['validated'] = pair['validated'] and pair['green_still_ready']
                            if args.workload=='portfolio':
                                after=json.loads(command('docker','exec',database,'psql','-U','lab',
                                                '-d',dbname,'-At','-v','ON_ERROR_STOP=1','-c',verification_sql))
                                pair['integrity']=after
                                pair['validated']=pair['validated'] and after==integrity
                                accepted_after = sum(
                                    1 for path in folder.glob('*-samples.jsonl')
                                    for line in path.read_text().splitlines()
                                    if (record := json.loads(line)).get('operation') == 'trade' and record['ok']
                                )
                                count_after = int(command('docker','exec',database,'psql','-U','lab',
                                    '-d',dbname,'-At','-c',"SELECT count(*) FROM portfolio_holding_histories WHERE change_type='BUY'"))
                                pair['trade_integrity'] = {
                                    'accepted_http_trades': accepted_after, 'seed_buy_histories': 6000,
                                    'actual_buy_histories': count_after, 'consistent': count_after == 6000 + accepted_after,
                                }
                                pair['validated'] = pair['validated'] and pair['trade_integrity']['consistent']
                    except Exception as error:
                        pair['validated'] = False
                        pair['error_type'] = type(error).__name__
                        if isinstance(error, RuntimeError):
                            pair['error_message'] = str(error)
                        killed = command('docker','inspect','--format','{{.State.OOMKilled}}',app)
                        pair['oom_kill'] = max(pair['oom_kill'], int(killed=='true'))
                    finally:
                        result['pair'] = pair
                        (folder/'pair.json').write_text(json.dumps(pair,indent=2))
                        (output/'results.json').write_text(json.dumps(results,indent=2))
                        print('PAIR '+json.dumps(pair),flush=True)
                    if not pair['validated']:
                        raise RuntimeError('Two-JVM memory gate failed; do not deploy this configuration')
            finally:
                stop.set()
                observer.join(timeout=10)
                (folder / 'state.json').write_text(command('docker','inspect','--format','{{json .State}}',app))
                (folder / 'app.log').write_text(command('docker', 'logs', app))
                if args.pair_memory_mib:
                    green_log = subprocess.run(['docker','exec',app,'cat','/tmp/memory-green.log'],
                                               capture_output=True,text=True)
                    (folder/'green.log').write_text(green_log.stdout)
                command('docker', 'rm', '-f', app)
    finally:
        if handoff:
            handoff.cleanup()
        for name in (generator, app, database):
            subprocess.run(['docker', 'rm', '-f', name], capture_output=True)
        subprocess.run(['docker', 'network', 'rm', prefix], capture_output=True)


if __name__ == '__main__':
    main()
