"""Opt-in SQL lifecycle check against a disposable, already-migrated PostgreSQL only."""
import argparse
import json
import datetime as dt
import importlib.util
import os
from pathlib import Path
import tempfile
import unittest
import uuid
from zoneinfo import ZoneInfo

spec=importlib.util.spec_from_file_location('fixed',Path(__file__).with_name('fixed-history-probe.py'))
fixed=importlib.util.module_from_spec(spec);spec.loader.exec_module(fixed)

@unittest.skipUnless(os.environ.get('FIXED_HISTORY_DISPOSABLE_DB')=='true','Disposable PostgreSQL required')
class FixedHistoryFixtureTest(unittest.TestCase):
    def test_seed_validate_restore_and_cleanup_leave_other_cohort_unchanged(self):
        assert os.environ['PGHOST'] in ('localhost','127.0.0.1')
        assert os.environ['PGDATABASE']=='test'
        with tempfile.TemporaryDirectory() as folder:
            probes=[]
            try:
                for label in ('owner','other'):
                    p=fixed.FixedHistoryProbe.__new__(fixed.FixedHistoryProbe)
                    p.args=argparse.Namespace(members=3)
                    p.run='fixed981-test-'+uuid.uuid4().hex[:12]
                    p.output=Path(folder)/label;p.output.mkdir()
                    p.env=dict(os.environ,PGCONNECT_TIMEOUT='5')
                    p.inflight=False;p.members=[];p.cohort=[]
                    p.report={'cases':[]};p.today=dt.datetime.now(ZoneInfo('Asia/Seoul')).date().isoformat()
                    p.asset=int(p.query("SELECT id FROM assets WHERE listed=true AND market='KOSPI' ORDER BY id LIMIT 1"))
                    p.authenticate=lambda member:{'member':member}
                    probes.append(p);p.prepare();p.seed(10)
                owner,other=probes
                pids=owner.portfolios()
                owner.query("INSERT INTO portfolio_holding_histories(portfolio_id,asset_id,change_type,quantity,"
                    "average_purchase_price,native_average_purchase_price,effective_date,transaction_quantity,"
                    "unit_price,fee,tax,exchange_rate,created_at,updated_at) "
                    f"SELECT id,{owner.asset},'BUY',10,100,100,DATE '{owner.today}',1,100,0,0,1,now(),now() "
                    f"FROM portfolios WHERE member_id IN ({owner.ids()}); "
                    f"UPDATE portfolio_holdings SET quantity=11 WHERE portfolio_id IN ({pids}); "
                    f"INSERT INTO portfolio_transaction_requests(member_id,request_key,request_hash,response_body) "
                    f"SELECT id,'test-key',repeat('0',64),'{{}}' FROM members WHERE id IN ({owner.ids()})",write=True)
                owner.verify(1,True)
                owner.reset('synthetic-case')
                owner.verify(0,False)
                other.verify(0,False)
                owner.query("INSERT INTO portfolio_transaction_requests(member_id,request_key,request_hash,response_body) "
                    f"SELECT id,'backup-json',repeat('0',64),json_build_object('quoted','value')::text FROM members WHERE id IN ({owner.ids()})",write=True)
                owner.cleanup();owner.members=[]
                records=[json.loads(line) for line in (owner.output/'portfolio_transaction_requests-backup.jsonl').read_text().splitlines()]
                self.assertEqual(len(records),3)
                self.assertTrue(all(json.loads(row['response_body'])=={'quoted':'value'} for row in records))
                other.verify(0,False)
            finally:
                for p in probes:
                    p.cleanup()

if __name__=='__main__':unittest.main()
