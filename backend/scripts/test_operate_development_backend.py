import tempfile
import unittest
from pathlib import Path
import operate_backend_development as operations


class ActiveArtifactTest(unittest.TestCase):
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
