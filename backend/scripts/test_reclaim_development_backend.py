import tempfile
import subprocess
import unittest
import urllib.error
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import Mock, patch

import deploy_backend as deployment


class ReclaimTest(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory();self.addCleanup(self.temp.cleanup)
        self.root=Path(self.temp.name)
        self.proc=self.root/'proc';self.proc.mkdir()
        (self.proc/'meminfo').write_text('SwapTotal: 1048572 kB\nSwapFree: 1041404 kB\n')
        (self.proc/'20').mkdir();(self.proc/'20/comm').write_text('Runner.Listener\n')
        self.cgroup=self.root/'cgroup';self.cgroup.mkdir()
        self.unit='actions.runner.woowacourse-teams-2026-DAYNOMY.frontend-dev-arm64-server.service'
        self.group=self.cgroup/'system.slice'/self.unit;self.group.mkdir(parents=True)
        for name,value in [('memory.current',str(200*1024**2)),('memory.swap.max','max'),
                           ('memory.swap.current','0'),('memory.reclaim',''),('cgroup.procs','20\n')]:
            (self.group/name).write_text(value)
        self.free=500*1024**2
        self.verify=Mock()
        self.kernel=Mock(side_effect=self.reclaim)
        for target,name,value in [(deployment,'ENVIRONMENT','development'),(deployment,'PROC_ROOT',self.proc),
                                  (deployment,'CGROUP_ROOT',self.cgroup),(deployment.os,'geteuid',lambda:0),
                                  (deployment,'require_target_instance',Mock()),
                                  (deployment,'available',lambda:self.free),
                                  (deployment,'command',self.command),(deployment.subprocess,'run',self.kernel)]:
            p=patch.object(target,name,value);p.start();self.addCleanup(p.stop)

    def command(self,*args):
        return '/system.slice/'+self.unit if args[2]==self.unit else ''

    def reclaim(self,args,**kwargs):
        self.assertEqual(args[-2],str(self.group/'memory.reclaim'))
        self.free=800*1024**2
        (self.group/'memory.swap.current').write_text(str(64*1024**2))
        return SimpleNamespace(returncode=0)

    def test_reclaims_only_allowlisted_service_and_verifies_healthy_application(self):
        samples=deployment.reclaim_development_memory(self.verify)
        self.assertEqual(len(samples),1)
        self.verify.assert_called_once_with()
        self.assertEqual(samples[0]['swap_current_kib'],64*1024)
        self.assertEqual((self.group/'memory.swap.max').read_text(),'max')
        self.assertNotIn('daynomy-dev.service',self.kernel.call_args.args[0])

    def test_production_never_reclaims(self):
        with patch.object(deployment,'ENVIRONMENT','production'):
            self.assertEqual(deployment.reclaim_development_memory(self.verify),[])
        self.kernel.assert_not_called()

    def test_larger_development_candidate_keeps_production_gate_unchanged(self):
        self.assertEqual(deployment.candidate_available_required(),736*1024**2)
        with patch.object(deployment,'ENVIRONMENT','production'):
            self.assertEqual(deployment.candidate_available_required(),656*1024**2)

    def test_missing_swap_keeps_original_memory_gate(self):
        (self.proc/'meminfo').write_text('SwapTotal: 0 kB\nSwapFree: 0 kB\n')
        self.assertEqual(deployment.reclaim_development_memory(self.verify),[])
        self.kernel.assert_not_called()

    def test_absent_optional_services_are_skipped(self):
        with patch.object(deployment,'command',side_effect=subprocess.CalledProcessError(1,['systemctl','show'])):
            self.assertEqual(deployment.reclaim_development_memory(self.verify),[])
        self.kernel.assert_not_called()

    def test_busy_frontend_worker_and_disallowed_swap_are_not_touched(self):
        (self.proc/'20/comm').write_text('Runner.Worker\n')
        self.assertEqual(deployment.reclaim_development_memory(self.verify),[])
        self.kernel.assert_not_called()
        (self.proc/'20/comm').write_text('Runner.Listener\n')
        (self.group/'memory.swap.max').write_text('0')
        self.assertEqual(deployment.reclaim_development_memory(self.verify),[])
        self.kernel.assert_not_called()

    def test_health_failure_is_not_hidden(self):
        self.verify.side_effect=RuntimeError('Application health failed')
        with self.assertRaisesRegex(RuntimeError,'Application health'):
            deployment.reclaim_development_memory(self.verify)
        self.free=500*1024**2
        self.verify.side_effect=urllib.error.URLError('health unavailable')
        with self.assertRaises(urllib.error.URLError):
            deployment.reclaim_development_memory(self.verify)


if __name__=='__main__':unittest.main()
