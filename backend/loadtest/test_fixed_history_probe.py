import importlib.util
from pathlib import Path
import threading
import subprocess
import sys
import tempfile
import time
import unittest

spec=importlib.util.spec_from_file_location('fixed',Path(__file__).with_name('fixed-history-probe.py'))
fixed=importlib.util.module_from_spec(spec);spec.loader.exec_module(fixed)

class ArrivalDriverTest(unittest.TestCase):
    def test_one_submission_per_fixture_and_actual_dispatch_count(self):
        visited=[]
        rows,drops,elapsed=fixed.drive(list(range(8)),200,lambda item:(visited.append(item) or {'status':201}))
        self.assertEqual(sorted(visited),list(range(8)))
        self.assertEqual(len(rows),8)
        self.assertEqual(drops,[])
        self.assertGreaterEqual(elapsed,7/200)

    def test_drops_are_unsent_and_all_sent_requests_are_drained(self):
        active=0;completed=0;lock=threading.Lock()
        def send(item):
            nonlocal active,completed
            with lock: active+=1
            time.sleep(.04)
            with lock: active-=1;completed+=1
            return {'status':201}
        rows,drops,_=fixed.drive(list(range(20)),1000,send,capacity=2)
        self.assertEqual(len(rows)+len(drops),20)
        self.assertGreater(len(drops),0)
        self.assertEqual(completed,len(rows))
        self.assertEqual(active,0)

    def test_exception_also_drains_before_fixture_reset_is_possible(self):
        finished=threading.Event()
        def send(item):
            if item==0: raise RuntimeError('synthetic failure')
            time.sleep(.03);finished.set();return {'status':201}
        with self.assertRaises(RuntimeError): fixed.drive([0,1],1000,send,capacity=2)
        self.assertTrue(finished.is_set())

class DrainTest(unittest.TestCase):
    def test_waits_for_server_completion_after_client_completion(self):
        probe=fixed.FixedHistoryProbe.__new__(fixed.FixedHistoryProbe)
        calls=[]
        def metrics(label):
            calls.append(label)
            return {'hikaricp_connections_active':1 if len(calls)<3 else 0,
                    'hikaricp_connections_pending':0,'tomcat_threads_busy_threads':0,
                    'http_server_requests_active_seconds_gcount':0}
        probe.metrics=metrics
        self.assertTrue(probe.drain(clock=lambda:0,sleep=lambda seconds:None))
        self.assertEqual(len(calls),12)

    def test_missing_pool_metrics_cannot_be_treated_as_drained(self):
        probe=fixed.FixedHistoryProbe.__new__(fixed.FixedHistoryProbe)
        probe.metrics=lambda label:{}
        with self.assertRaises(AssertionError):probe.drain(clock=lambda:0,sleep=lambda seconds:None)

class DeploymentGuardTest(unittest.TestCase):
    def test_conflicts_with_controller_flock_and_releases_after_run(self):
        code="""import fcntl,sys
with open(sys.argv[1],'a+') as stream:
 try:fcntl.flock(stream,fcntl.LOCK_EX|fcntl.LOCK_NB)
 except BlockingIOError:sys.exit(3)
"""
        with tempfile.TemporaryDirectory() as folder:
            path=Path(folder)/'deploy.lock'
            def contender():return subprocess.run([sys.executable,'-c',code,str(path)],capture_output=True,timeout=5).returncode
            with fixed.deployment_guard(path): self.assertEqual(contender(),3)
            self.assertEqual(contender(),0)

if __name__=='__main__': unittest.main()
