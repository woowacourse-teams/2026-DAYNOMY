import tempfile
import unittest
from unittest.mock import patch
from pathlib import Path
import operate_backend_development as operations


class ActiveArtifactTest(unittest.TestCase):
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
