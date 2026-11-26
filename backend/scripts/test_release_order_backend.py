import unittest
from types import SimpleNamespace
from unittest.mock import Mock, patch
import wait_for_production_backend as gate


class ReleaseOrderTest(unittest.TestCase):
    def run_data(self, **values):
        return dict(id=10, head_sha='a'*40, head_branch='main', event='push',
                    status='completed', conclusion='success', **values)

    def test_frontend_only_push_does_not_wait_for_a_nonexistent_backend_run(self):
        with patch.object(gate.subprocess, 'run', return_value=SimpleNamespace(returncode=0)):
            self.assertFalse(gate.backend_changed('b'*40, 'a'*40))
        with patch.object(gate.subprocess, 'run', return_value=SimpleNamespace(returncode=1)):
            self.assertTrue(gate.backend_changed('b'*40, 'a'*40))
        with patch.object(gate.subprocess, 'run', return_value=SimpleNamespace(returncode=128)):
            with self.assertRaises(RuntimeError):gate.backend_changed('b'*40, 'a'*40)

    def test_other_commit_or_development_success_never_unlocks_production(self):
        run=self.run_data()
        self.assertIsNone(gate.deployment_result([{**run,'head_sha':'b'*40}], 'a'*40))
        self.assertIsNone(gate.deployment_result([{**run,'head_branch':'dev'}], 'a'*40))
        self.assertIsNone(gate.deployment_result([{**run,'event':'pull_request'}], 'a'*40))

    def test_failure_cancel_and_skipped_do_not_deploy_frontend(self):
        for conclusion in ('failure','cancelled','skipped','timed_out'):
            with self.subTest(conclusion=conclusion),self.assertRaises(RuntimeError):
                gate.deployment_result([{**self.run_data(),'conclusion':conclusion}], 'a'*40)

    def test_older_success_does_not_hide_newer_failure(self):
        runs=[self.run_data(),{**self.run_data(),'id':11,'conclusion':'failure'}]
        with self.assertRaises(RuntimeError):gate.deployment_result(runs,'a'*40)

    def test_pending_run_waits_until_same_commit_succeeds(self):
        run=self.run_data()
        fetch=Mock(side_effect=[[],[{**run,'status':'in_progress','conclusion':None}],[run]])
        with patch.object(gate.time,'sleep'):
            self.assertEqual(gate.wait_for_success(fetch,'a'*40),10)
        self.assertEqual(fetch.call_count,3)

    def test_timeout_never_unlocks_frontend(self):
        with self.assertRaisesRegex(RuntimeError,'Timed out'):
            gate.wait_for_success(lambda:[], 'a'*40,timeout=0)


if __name__=='__main__':unittest.main()
