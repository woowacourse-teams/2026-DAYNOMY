import tempfile
import io
import unittest
from unittest.mock import patch
from pathlib import Path
import operate_backend_development as operations


class ActiveArtifactTest(unittest.TestCase):
    def test_proxy_repair_preserves_app_owner_and_rolls_back_failed_health(self):
        for status in ('UP','DOWN'):
            with self.subTest(status=status),tempfile.TemporaryDirectory() as folder:
                root=Path(folder);state=root/'state';state.mkdir()
                owner=state/'active-slot';owner.write_text('green\n')
                route=root/'proxy.conf';route.write_text('previous proxy')
                helper=root/'helper.py';helper.write_text('previous helper')
                calls=[]
                with patch.object(operations.deploy_backend,'STATE',state),patch.object(operations.deploy_backend,'ROUTE',route),patch.object(operations.bootstrap_backend,'BOOT_HELPER',helper),patch.object(operations.deploy_backend,'probe'),patch.object(operations.deploy_backend,'proxy_probe'),patch.object(operations.deploy_backend,'command',side_effect=lambda *args:calls.append(args)),patch.object(operations.urllib.request,'urlopen',return_value=io.BytesIO(('{"status":"'+status+'"}').encode())):
                    if status=='UP':
                        operations.repair_proxy()
                        self.assertIn('proxy_set_header Host localhost;',route.read_text())
                        self.assertEqual(helper.read_text(),Path(operations.deploy_backend.__file__).read_text())
                    else:
                        with self.assertRaisesRegex(RuntimeError,'not UP'):operations.repair_proxy()
                        self.assertEqual(route.read_text(),'previous proxy')
                        self.assertEqual(helper.read_text(),'previous helper')
                self.assertEqual(owner.read_text(),'green\n')
                self.assertTrue(all(call in [('nginx','-t'),('systemctl','reload','nginx')] for call in calls))

    def test_transition_sampling_handles_retiring_jvm_and_uncreated_slice(self):
        def read(path):
            if str(path)=='/proc/meminfo':return 'MemAvailable: 700000 kB\n'
            if str(path)=='/proc/101/status':return 'VmRSS: 310000 kB\nVmSwap: 0 kB\n'
            raise FileNotFoundError(str(path))
        with patch.object(operations.subprocess,'check_output',side_effect=['101\n','202\n','\n']),patch.object(Path,'read_text',read):
            row=operations.transition_sample()
        self.assertEqual(row['available_kib'],700000)
        self.assertEqual(row['jvms'],{'blue':{'VmRSS':310000,'VmSwap':0}})

    def test_observation_failure_never_masks_operation_failure(self):
        with patch.object(operations,'transition_sample',side_effect=OSError('unavailable')),patch('builtins.print') as output:
            with self.assertRaisesRegex(RuntimeError,'original operation'):
                with operations.observe_transition():raise RuntimeError('original operation')
        self.assertEqual(output.call_count,2)
        self.assertIn('development_transition_observation_error',output.call_args.args[0])

    def test_uses_original_jar_before_initial_installation(self):
        with tempfile.TemporaryDirectory() as folder:
            app=Path(folder);release=app/'original.jar';release.write_bytes(b'old')
            (app/'current.jar').symlink_to(release)
            self.assertEqual(operations.current_artifact(app),release.resolve())

    def test_after_switch_uses_active_slot_instead_of_stale_current_symlink(self):
        with tempfile.TemporaryDirectory() as folder:
            app=Path(folder);old=app/'original.jar';old.write_bytes(b'old')
            new=app/'next.jar';new.write_bytes(b'new')
            (app/'current.jar').symlink_to(old)
            state=app/'deployment-dev';state.mkdir()
            (state/'active-slot').write_text('green\n')
            (state/'green.jar').symlink_to(new)
            self.assertEqual(operations.current_artifact(app),new.resolve())
            (state/'active-slot').write_text('unexpected\n')
            with self.assertRaisesRegex(RuntimeError,'Unknown'):
                operations.current_artifact(app)


if __name__=='__main__':unittest.main()
