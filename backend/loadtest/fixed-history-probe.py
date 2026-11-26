#!/usr/bin/env python3
"""Dev-only fixed history diagnostic. One new trade per member/case; no production writes.

Runs must start on an idle, isolated dev JVM. Profiles belong in a separate pass.
Credentials and ten-minute synthetic-user access tokens stay in memory.
"""
import argparse
import base64
import concurrent.futures as futures
import datetime as dt
import hashlib
import hmac
import http.client
import http.cookies
import importlib.util
import json
import math
import resource
from pathlib import Path
import ssl
import statistics
import threading
import subprocess
import re
import os
import fcntl
import time
import urllib.request
import uuid

spec = importlib.util.spec_from_file_location("idem_probe", Path(__file__).with_name("idempotency-probe.py"))
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


def drive(items, rps, send, capacity=32, clock=time.monotonic, sleep=time.sleep):
    """Bounded open-loop arrivals. Always drain submitted work before returning or raising."""
    assert rps > 0 and capacity > 0
    gate = threading.BoundedSemaphore(capacity)
    start = clock()
    sent, dropped = [], []
    with futures.ThreadPoolExecutor(max_workers=capacity) as pool:
        for index, item in enumerate(items):
            target = start + index / rps
            sleep(max(0, target - clock()))
            if not gate.acquire(blocking=False):
                dropped.append(index)
                continue
            def work(index=index, item=item, target=target):
                try:
                    began = clock()
                    result = send(item)
                    return dict(index=index, schedule_lag_ms=(began-target)*1000, **result)
                finally:
                    gate.release()
            sent.append(pool.submit(work))
        # Executor context also drains all futures if a worker raises. Never reset in flight.
        results = [future.result() for future in sent]
    return results, dropped, clock() - start


def percentile(values, fraction):
    return sorted(values)[max(0, math.ceil(len(values) * fraction) - 1)]


class FixedHistoryProbe(module.Probe):
    def __init__(self, args):
        super().__init__(args)
        self.run = "fixed981-" + uuid.uuid4().hex[:12]
        self.report.update(run=self.run, protocol="one trade/member/case; restore outside timing",
                           members=args.members, history_sizes=args.histories, rps=args.rps)
        self.args = args
        self.cohort = []
        self.inflight = False
        self.tls = threading.local()
        self.ssl_context = ssl.create_default_context()
        self.pin = self.guard()
        self.report["release"] = self.pin

    def guard(self):
        root = Path('/home/ec2-user/backend/deployment-dev')
        assert Path('/sys/devices/virtual/dmi/id/board_asset_tag').read_text().strip() == 'i-0f2e92d9c7bd8fe06'
        assert not (root/'retirement.json').exists(), 'Previous JVM retirement is pending'
        slot = (root/'active-slot').read_text().strip()
        assert slot in ('blue','green')
        unit = 'daynomy-dev' if slot=='blue' else 'daynomy-dev-green'
        other = 'daynomy-dev-green' if slot=='blue' else 'daynomy-dev'
        unit_state=dict(row.split('=',1) for row in subprocess.check_output(['systemctl','show',unit,'-p','MainPID','-p','NRestarts'],text=True).splitlines())
        pid = int(unit_state['MainPID'])
        assert pid > 0
        assert int(subprocess.check_output(['systemctl','show',other,'-p','MainPID','--value'],text=True)) == 0
        raw_flags = (Path('/proc')/str(pid)/'cmdline').read_bytes().decode().split('\0')
        env = dict(x.split(b'=',1) for x in (Path('/proc')/str(pid)/'environ').read_bytes().split(b'\0') if b'=' in x)
        for key in (b'JAVA_TOOL_OPTIONS',b'JDK_JAVA_OPTIONS',b'_JAVA_OPTIONS'):
            raw_flags += env.get(key,b'').decode().split()
        flags = [x for x in raw_flags if re.fullmatch(r'-X(?:mx|ms|ss)\d+[kKmMgG]?|-XX:(?:[+]UseSerialGC|TieredStopAtLevel=\d+)',x)]
        assert {'-Xmx192m','-XX:+UseSerialGC','-XX:TieredStopAtLevel=1'}.issubset(flags)
        assert env.get(b'MALLOC_ARENA_MAX')==b'2'
        limits = dict(x.split('=',1) for x in subprocess.check_output(['systemctl','show','daynomy-backend-dev.slice','-p','MemoryMax','-p','MemorySwapMax'],text=True).splitlines())
        assert limits == {'MemoryMax':'1073741824','MemorySwapMax':'0'}
        cg=subprocess.check_output(['systemctl','show','daynomy-backend-dev.slice','-p','ControlGroup','--value'],text=True).strip()
        events=dict(row.split() for row in (Path('/sys/fs/cgroup')/cg.lstrip('/')/'memory.events').read_text().splitlines())
        oom={k:int(events[k]) for k in ('oom','oom_kill')}
        keys = 'LEAGUE_AUTO_SYNC_ENABLED STOCK_PRICE_SYNC_ENABLED PORTFOLIO_SNAPSHOT_ENABLED LEAGUE_CALCULATION_ENABLED MARKET_CALENDAR_SYNC_ENABLED US_MARKET_CALENDAR_SYNC_ENABLED STOCK_SYNC_ENABLED US_STOCK_SYNC_ENABLED EXCHANGE_RATE_SYNC_ENABLED INVESTMENT_CALENDAR_SYNC_ENABLED CONTENT_COLLECTION_ENABLED PERSON_POST_COLLECTION_ENABLED KOREAN_NEWS_GENERATION_ENABLED US_NEWS_GENERATION_ENABLED EMAIL_DELIVERY_SCHEDULING_ENABLED'.split()
        assert all(env.get(k.encode())==b'false' for k in keys), 'Schedulers must be explicitly disabled'
        with (root/'jobs.lock').open('a+') as lock:
            fcntl.lockf(lock,fcntl.LOCK_EX|fcntl.LOCK_NB)
            fcntl.lockf(lock,fcntl.LOCK_UN)
        with urllib.request.urlopen('http://127.0.0.1:8081/actuator/deploymenttasks',timeout=5) as response:
            tasks = json.load(response)
        assert tasks['scheduled']['active'] == 0
        assert all(all(v.get(k,0)==0 for k in ('active','pending','queued')) for v in tasks['executors'].values())
        jar = (root/(slot+'.jar')).resolve()
        digest = hashlib.sha256()
        with jar.open('rb') as stream:
            for chunk in iter(lambda:stream.read(1048576),b''): digest.update(chunk)
        sha = digest.hexdigest()
        assert sha == self.args.expected_jar_sha, 'Release changed'
        return {'slot':slot,'pid':pid,'sha256':sha,'disabledSchedulers':keys,'flags':flags,'slice':limits,'mallocArenaMax':'2','oomEvents':oom,'restarts':int(unit_state['NRestarts'])}

    def metrics(self, label, persist=True):
        with urllib.request.urlopen('http://127.0.0.1:8081/actuator/prometheus',timeout=5) as response:
            text = response.read().decode()
        wanted = {'process_cpu_time_ns_total','jvm_gc_pause_seconds_sum','jvm_gc_pause_seconds_count',
                  'jvm_gc_memory_allocated_bytes_total','hikaricp_connections_pending',
                  'hikaricp_connections_creation_seconds_count','hikaricp_connections_acquire_seconds_count',
                  'hikaricp_connections_acquire_seconds_sum','hikaricp_connections_timeout_total',
                  'hikaricp_connections_active','tomcat_threads_busy_threads','http_server_requests_active_seconds_gcount'}
        values = {}
        for line in text.splitlines():
            if not line or line.startswith('#'): continue
            head,value = line.rsplit(' ',1)
            name = head.split('{',1)[0]
            if name in wanted or name.startswith('daynomy_portfolio_transaction_'):
                values[head] = float(value)
        if persist: (self.output/(label+'.json')).write_text(json.dumps(values))
        return values

    def statement_stats(self):
        # Existing pg_stat_statements only; no reset or extension/configuration changes.
        return json.loads(self.query("SELECT coalesce(json_agg(json_build_object("
            "'queryId',queryid::text,'calls',calls,'execMs',total_exec_time,'rows',rows,"
            "'sharedHits',shared_blks_hit,'sharedReads',shared_blks_read,'walBytes',wal_bytes,"
            "'category',CASE WHEN query LIKE '%portfolio_transaction_requests%' THEN 'idempotency' "
            "WHEN query LIKE '%portfolio_holding_histories%' AND query LIKE '%join assets%' THEN 'history_fetch' "
            "WHEN query LIKE '%portfolio_holding_histories%' THEN 'history_other' "
            "WHEN query LIKE '%portfolio_holdings%' THEN 'holdings' "
            "WHEN query LIKE '%members%' THEN 'member' ELSE 'portfolio' END)),'[]'::json) "
            "FROM pg_stat_statements WHERE dbid=(SELECT oid FROM pg_database WHERE datname=current_database()) "
            "AND userid=(SELECT oid FROM pg_roles WHERE rolname=current_user) "
            "AND query NOT LIKE '%pg_stat_statements%' "
            "AND (query LIKE '%portfolio_transaction_requests%' OR query LIKE '%portfolio_holding_histories%' "
            "OR query LIKE '%portfolio_holdings%' OR query LIKE '%members%' OR query LIKE '%portfolios%')"))

    def drain(self, clock=time.monotonic, sleep=time.sleep):
        quiet = 0
        deadline = clock()+60
        names = {'hikaricp_connections_active','hikaricp_connections_pending',
                 'tomcat_threads_busy_threads','http_server_requests_active_seconds_gcount'}
        while clock()<deadline:
            m = self.metrics('drain-last')
            assert all(any(head.split('{',1)[0]==name for head in m) for name in ('hikaricp_connections_active','hikaricp_connections_pending')), 'Pool drain metrics absent'
            observed = {name:sum(value for head,value in m.items() if head.split('{',1)[0]==name) for name in names}
            if all(value==0 for value in observed.values()): quiet+=1
            else: quiet=0
            if quiet>=10: return True
            sleep(.2)
        return False

    def send(self, fixture, endpoint, key):
        start = time.monotonic()
        try:
            if not hasattr(self.tls,'connection'):
                self.tls.connection = http.client.HTTPSConnection('dev.daynomy.com',context=self.ssl_context,timeout=15)
            conn = self.tls.connection
            conn.request('POST',endpoint,json.dumps(self.body()),self.headers(fixture,key))
            response = conn.getresponse()
            raw = response.read()
            body = json.loads(raw)
            return {'status':response.status,'body':body,'elapsed_ms':(time.monotonic()-start)*1000}
        except Exception as error:
            if hasattr(self.tls,'connection'):
                self.tls.connection.close()
                del self.tls.connection
            return {'status':0,'body':{'errorType':type(error).__name__},'elapsed_ms':(time.monotonic()-start)*1000}

    def token(self, member):
        enc = lambda value: base64.urlsafe_b64encode(value).rstrip(b"=")
        now = int(time.time())
        claims = {"iss": self.config.get("JWT_ISSUER", "daynomy"), "sub": str(member),
                  "iat": now, "exp": now + 600, "jti": str(uuid.uuid4()),
                  "sid": str(uuid.uuid4()), "token_type": "access", "role": "USER"}
        data = enc(b'{"alg":"HS256","typ":"JWT"}') + b"." + enc(json.dumps(claims).encode())
        secret = self.config["JWT_SECRET"]
        key = base64.b64decode(secret + "=" * ((4-len(secret) % 4) % 4))
        return (data+b"."+enc(hmac.new(key, data, hashlib.sha256).digest())).decode()

    def authenticate(self, member):
        token = self.token(member)
        request = urllib.request.Request(self.base + "/api/auth/csrf", headers={"Cookie": "access_token="+token})
        with urllib.request.urlopen(request, timeout=10) as response:
            csrf = json.load(response)
            cookies = http.cookies.SimpleCookie()
            for value in response.headers.get_all("Set-Cookie", []): cookies.load(value)
        return {"member": member, "csrf": csrf, "cookie": "access_token="+token+"; "
                + "; ".join(k+"="+v.value for k,v in cookies.items())}

    def prepare(self):
        n = self.args.members
        self.members = json.loads(self.query(
            "WITH inserted AS (INSERT INTO members(provider,provider_id,email,nickname,role,status,created_at,updated_at) "
            f"SELECT 'GOOGLE','{self.run}-'||i,'{self.run}-'||i||'@example.invalid',"
            f"'fh'||substr(md5('{self.run}-'||i),1,16),'USER','ACTIVE',now(),now() "
            f"FROM generate_series(1,{n}) g(i) RETURNING id) SELECT json_agg(id ORDER BY id) FROM inserted", write=True))
        (self.output / "member-manifest.json").write_text(json.dumps(self.members))
        ids = self.ids()
        self.query(f"INSERT INTO portfolios(member_id,created_at,updated_at) SELECT id,now(),now() "
                   f"FROM members WHERE id IN ({ids})", write=True)
        with futures.ThreadPoolExecutor(max_workers=8) as pool:
            self.cohort = list(pool.map(self.authenticate, self.members))
        self.save()

    def ids(self): return ",".join(map(str, self.members))
    def portfolios(self): return f"SELECT id FROM portfolios WHERE member_id IN ({self.ids()})"

    def ownership(self):
        assert not self.inflight, "No fixture writes while requests may be in flight"
        assert int(self.query(f"SELECT count(*) FROM members WHERE id IN ({self.ids()}) "
            f"AND provider_id LIKE '{self.run}-%' AND email=provider_id||'@example.invalid'")) == len(self.members)
        # League and public sharing must stay absent, even when an override is misconfigured.
        assert self.query(f"SELECT count(*) FROM public_investor_profiles WHERE member_id IN ({self.ids()})") == "0"
        assert self.query(f"SELECT count(*) FROM shared_portfolios WHERE member_id IN ({self.ids()})") == "0"

    def seed(self, history_count):
        self.ownership()
        pids = self.portfolios()
        # Synthetic cohort only. No TRUNCATE, global counters reset, schema or DB settings changes.
        self.query(f"DELETE FROM portfolio_transaction_requests WHERE member_id IN ({self.ids()}); "
            f"DELETE FROM portfolio_daily_snapshots WHERE portfolio_id IN ({pids}); "
            f"DELETE FROM portfolio_holding_histories WHERE portfolio_id IN ({pids}); "
            f"DELETE FROM portfolio_holdings WHERE portfolio_id IN ({pids}); "
            "INSERT INTO portfolio_holding_histories(portfolio_id,asset_id,change_type,quantity,"
            "average_purchase_price,native_average_purchase_price,effective_date,transaction_quantity,"
            "unit_price,fee,tax,exchange_rate,created_at,updated_at) "
            f"SELECT p.id,{self.asset},'BUY',g.i-1,100,100,DATE '{self.today}',1,100,0,0,1,"
            f"now()-interval '1 hour'+g.i*interval '1 microsecond',now() FROM portfolios p "
            f"CROSS JOIN generate_series(1,{history_count}) g(i) WHERE p.member_id IN ({self.ids()}); "
            "INSERT INTO portfolio_holdings(portfolio_id,asset_id,quantity,average_purchase_price,"
            f"native_average_purchase_price,created_at,updated_at) SELECT id,{self.asset},{history_count},100,100,now(),now() "
            f"FROM portfolios WHERE member_id IN ({self.ids()})", write=True)
        self.history_count = history_count
        self.cutoff = int(self.query(f"SELECT max(id) FROM portfolio_holding_histories WHERE portfolio_id IN ({pids})"))
        self.verify(0, False)

    def explain(self):
        if not self.args.history_sql: return
        sql=Path(self.args.history_sql).read_text().strip()
        assert sql.startswith('select ') and sql.count('?')==1 and ';' not in sql
        assert 'from portfolio_holding_histories' in sql and 'join assets' in sql
        portfolio=int(self.query(f'SELECT min(id) FROM portfolios WHERE member_id IN ({self.ids()})'))
        results=[]
        for _ in range(5):
            plan=json.loads(self.query('EXPLAIN (ANALYZE, BUFFERS, SERIALIZE TEXT, FORMAT JSON) '+sql.replace('?',str(portfolio))))
            assert plan[0]['Plan']['Actual Rows']==self.history_count
            results.append(plan)
        (self.output/f'h{self.history_count}-explain.json').write_text(json.dumps(results))

    def verify(self, increment, keyed):
        pids = self.portfolios()
        values = json.loads(self.query(
            "SELECT json_build_object('members',count(*),'badHistories',count(*) FILTER (WHERE "
            f"(SELECT count(*) FROM portfolio_holding_histories h WHERE h.portfolio_id=p.id)<>{self.history_count+increment}),"
            "'badHoldings',count(*) FILTER (WHERE NOT EXISTS (SELECT 1 FROM portfolio_holdings h "
            f"WHERE h.portfolio_id=p.id AND h.asset_id={self.asset} AND h.quantity={self.history_count+increment} "
            "AND h.average_purchase_price=100 AND h.native_average_purchase_price=100)),"
            "'badKeys',count(*) FILTER (WHERE (SELECT count(*) FROM portfolio_transaction_requests r "
            f"WHERE r.member_id=p.member_id)<>{increment if keyed else 0}),"
            f"'snapshots',(SELECT count(*) FROM portfolio_daily_snapshots WHERE portfolio_id IN ({pids}))) "
            f"FROM portfolios p WHERE p.member_id IN ({self.ids()})"))
        assert values == {"members":len(self.members),"badHistories":0,"badHoldings":0,"badKeys":0,"snapshots":0}, values
        return values

    def reset(self, case):
        self.ownership()
        pids = self.portfolios()
        rows = json.loads(self.query("SELECT json_build_object('newHistories',"
            f"(SELECT coalesce(json_agg(h),'[]') FROM portfolio_holding_histories h WHERE portfolio_id IN ({pids}) AND id>{self.cutoff}),"
            f"'keys',(SELECT coalesce(json_agg(r),'[]') FROM portfolio_transaction_requests r WHERE member_id IN ({self.ids()})))"))
        path = self.output / (case+"-effects.json")
        path.write_text(json.dumps(rows)); path.chmod(0o600)
        self.query(f"DELETE FROM portfolio_transaction_requests WHERE member_id IN ({self.ids()}); "
            f"DELETE FROM portfolio_holding_histories WHERE portfolio_id IN ({pids}) AND id>{self.cutoff}; "
            f"UPDATE portfolio_holdings SET quantity={self.history_count},average_purchase_price=100,"
            f"native_average_purchase_price=100 WHERE portfolio_id IN ({pids})", write=True)
        self.verify(0,False)

    def case(self, rps, keyed, label):
        assert self.guard() == self.pin, "Runtime changed"
        self.ownership(); self.verify(0,False)
        assert self.today == dt.datetime.now(module.ZoneInfo("Asia/Seoul")).date().isoformat(), "Midnight crossed"
        for fixture in self.cohort:
            fixture["cookie"] = "access_token="+self.token(fixture["member"])+"; "+fixture["cookie"].split("; ",1)[1]
        before = self.metrics(label+"-before")
        sql_before=self.statement_stats()
        samples=[]
        stop=threading.Event()
        def sample():
            while not stop.wait(.5):
                try:
                    values=self.metrics('sample',persist=False)
                    names={'hikaricp_connections_active','hikaricp_connections_pending'}
                    row={name:sum(value for head,value in values.items() if head.split('{',1)[0]==name) for name in names}
                    row['elapsedSeconds']=time.monotonic()-began
                    row['hostMemAvailableBytes']=next(int(line.split()[1])*1024 for line in Path('/proc/meminfo').read_text().splitlines() if line.startswith('MemAvailable:'))
                    samples.append(row)
                except Exception as error:
                    samples.append({'sampleError':type(error).__name__})
        began=time.monotonic()
        started_utc=dt.datetime.now(dt.timezone.utc).isoformat()
        driver_before=resource.getrusage(resource.RUSAGE_SELF)
        monitor=threading.Thread(target=sample)
        monitor.start()
        self.inflight = True
        try:
            results,dropped,elapsed = drive(self.cohort,rps,
                lambda fixture:self.send(fixture,module.V2,str(uuid.uuid4()) if keyed else None))
            finished_utc=dt.datetime.now(dt.timezone.utc).isoformat()
        finally:
            stop.set();monitor.join(timeout=10)
            # Client completion alone cannot prove a timed-out server request has stopped.
            if not self.drain():
                raise RuntimeError('Server requests not drained; retain owned manifest, no fixture writes')
            self.inflight = False
        driver_after=resource.getrusage(resource.RUSAGE_SELF)
        after = self.metrics(label+"-after")
        sql_after=self.statement_stats()
        assert self.guard()==self.pin, 'Runtime changed during case'
        (self.output/(label+'-samples.json')).write_text(json.dumps(samples))
        successful = [r for r in results if r["status"]==201]
        summary = {"label":label,"historyCount":self.history_count,"rpsTarget":rps,
            "startedUtc":started_utc,"finishedUtc":finished_utc,"scheduled":len(self.cohort),"sent":len(results),"unsent":len(dropped),"elapsedSeconds":elapsed,
            "statuses":{str(status):sum(r["status"]==status for r in results) for status in set(r["status"] for r in results)},
            "latencyMs": {"p50":statistics.median(r["elapsed_ms"] for r in results),
                          "p95":percentile([r["elapsed_ms"] for r in results],.95),
                          "p99":percentile([r["elapsed_ms"] for r in results],.99)},
            "maxScheduleLagMs":max(r["schedule_lag_ms"] for r in results),
            "keyed":keyed,"profiled":self.args.profile_only,"warmup":label.startswith("warmup-"),"metricsBefore":before,"metricsAfter":after}
        summary['statementStatsBefore']=sql_before
        summary['statementStatsAfter']=sql_after
        summary['driverCpuSeconds']=driver_after.ru_utime+driver_after.ru_stime-driver_before.ru_utime-driver_before.ru_stime
        summary['poolPendingMax']=max([0]+[row.get('hikaricp_connections_pending',0) for row in samples])
        summary['phases']={}
        for head,value in before.items():
            name=head.split('{',1)[0]
            if name=='daynomy_portfolio_transaction_phase_seconds_count' and 'api="v2"' in head:
                phase=dict(re.findall(r'(\w+)="([^\"]*)"',head)).get('phase')
                sumhead=head.replace('_seconds_count','_seconds_sum',1)
                calls=after.get(head,0)-value
                seconds=after.get(sumhead,0)-before.get(sumhead,0)
                summary['phases'][phase]={'calls':calls,'totalMs':seconds*1000,'msPerCall':seconds*1000/calls if calls else None}
        self.report["cases"].append(summary);self.save()
        # Fail closed: preserve evidence and abort instead of restoring ambiguous writes.
        assert not dropped and len(successful)==len(self.cohort), "Arrival drops or failed HTTP; stop this matrix"
        for response in successful:
            body=response["body"]
            if "data" in body: body=body["data"]
            assert body["holding"]["quantity"]==self.history_count+1
            assert float(body["totalRealizedProfitLoss"])==0
        assert summary['phases'].get('history_read',{}).get('calls')==len(results), 'Phase counts differ; other API traffic or missing instrumentation'
        summary["verifiedDb"] = self.verify(1,keyed);self.save()
        print(json.dumps(summary),flush=True)
        self.reset(label)
        assert summary['latencyMs']['p95'] < 1000, 'Development safety stop; p95 >=1000ms (not an SLO)'

    def profile(self):
        # Privacy-checked minimal events only; this pass is excluded from latency comparison.
        self.prepare()
        self.seed(500)
        self.explain()
        jcmd='/usr/lib/jvm/temurin-21-jdk/bin/jcmd'
        settings=Path(__file__).with_name('profiles')/'history-allocation.jfc'
        assert settings.is_file()
        destination=(self.output/'history-allocation.jfr').resolve()
        # Target JVM writes the recording under its own uid, not the attaching root uid.
        process_owner=(Path('/proc')/str(self.pin['pid'])).stat()
        os.chown(self.output,process_owner.st_uid,process_owner.st_gid)
        self.output.chmod(0o700)
        started=False
        try:
            result=subprocess.run([jcmd,str(self.pin['pid']),'JFR.start','name=history981',
                'settings='+str(settings.resolve()),'filename='+str(destination),'maxsize=64m'],
                capture_output=True,text=True,timeout=15)
            assert result.returncode==0 and 'Started recording' in result.stdout, 'JFR start failed (details withheld)'
            started=True
            self.case(20,True,'warmup-profile')
            self.case(20,True,'profile-h500')
        finally:
            if started:
                result=subprocess.run([jcmd,str(self.pin['pid']),'JFR.stop','name=history981'],
                    capture_output=True,text=True,timeout=15)
                assert result.returncode==0, 'JFR stop failed (details withheld)'
        assert destination.is_file()
        destination.chmod(0o600)
        self.report['profileFile']=destination.name
        self.report['completed']=True;self.save()

    def run_matrix(self):
        self.prepare()
        for count in self.args.histories:
            self.seed(count)
            self.explain()
            self.case(20,True,f"warmup-h{count}")
            for repeat in range(self.args.repeats):
                for rps in self.args.rps:
                    for keyed in ([False,True] if repeat%2==0 else [True,False]):
                        self.case(rps,keyed,f"h{count}-r{rps}-n{repeat}-{'key' if keyed else 'none'}")
        self.report["completed"] = True;self.save()


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--run",action="store_true")
    parser.add_argument("--expected-jar-sha",required=True)
    parser.add_argument("--profile-only",action="store_true")
    parser.add_argument("--history-sql",help="Verified Hibernate SELECT with one parameter placeholder")
    parser.add_argument("--env-file",required=True)
    parser.add_argument("--output",required=True)
    parser.add_argument("--base-url",default="https://dev.daynomy.com")
    parser.add_argument("--members",type=int,default=600)
    parser.add_argument("--histories",type=int,nargs="+",default=[10,100,500])
    parser.add_argument("--rps",type=int,nargs="+",default=[20,40,60])
    parser.add_argument("--repeats",type=int,default=3)
    args=parser.parse_args();args.scale=False
    assert 1<=args.members<=1200 and 1<=args.repeats<=3
    assert all(x in [10,100,500] for x in args.histories)
    assert all(1<=x<=60 for x in args.rps)
    probe=FixedHistoryProbe(args)
    try:
        if args.profile_only: probe.profile()
        else: probe.run_matrix()
    finally:
        probe.ownership() if probe.members else None
        probe.cleanup()

if __name__=="__main__":main()
