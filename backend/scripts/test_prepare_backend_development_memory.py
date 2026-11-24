import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch, Mock

import prepare_backend_development_memory as memory


ORIGINAL = '''[Unit]
Description=development
[Service]
User=ec2-user
ExecStart=/usr/lib/jvm/java-21-amazon-corretto.aarch64/bin/java -jar /home/ec2-user/backend/current.jar --server.port=8080
[Install]
WantedBy=multi-user.target
'''


class MemoryPreparationTest(unittest.TestCase):
    def test_tuning_keeps_application_arguments_and_is_idempotent(self):
        result = memory.tuned_unit(ORIGINAL)
        self.assertIn('java ' + memory.FLAGS + ' -jar', result)
        self.assertIn('--server.port=8080', result)
        self.assertIn('MemoryMax=450M\nMemorySwapMax=0\n', result)
        self.assertEqual(result, memory.tuned_unit(result))

    def test_unreviewed_commands_or_memory_settings_are_rejected(self):
        for text in (ORIGINAL.replace(' -jar ', ' -Xmx512m -jar '),
                     ORIGINAL.replace('[Service]\n', '[Service]\nMemoryMax=700M\n'),
                     ORIGINAL.replace('/current.jar', '/other.jar')):
            with self.subTest(text=text):
                with self.assertRaisesRegex(RuntimeError, 'Unreviewed'):
                    memory.tuned_unit(text)

    def test_restore_refuses_to_overwrite_another_change(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            (root / 'service.before').write_text(ORIGINAL)
            unit = root / 'unit'; unit.write_text(ORIGINAL + '# someone else changed this\n')
            with patch.object(memory, 'BASE', root), patch.object(memory, 'UNIT', unit), patch.object(memory, 'run') as command:
                with self.assertRaisesRegex(RuntimeError, 'outside this experiment'):
                    memory.restore_unit()
                command.assert_not_called()

    def test_restore_stops_before_replacing_unit_and_preserves_swap(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            (root / 'service.before').write_text(ORIGINAL)
            unit = root / 'unit'; unit.write_text(memory.tuned_unit(ORIGINAL))
            events = []
            def write(path, content):
                events.append(('write',)); path.write_text(content)
            with patch.object(memory, 'BASE', root), patch.object(memory, 'UNIT', unit), patch.object(memory, 'run', side_effect=lambda *a, **k: events.append(a)), patch.object(memory.deploy_backend, 'atomic', side_effect=write), patch.object(memory, 'wait_healthy'), patch.object(memory, 'emit'):
                memory.restore_unit()
            self.assertEqual(events[0], ('systemctl', 'stop', 'daynomy-dev'))
            self.assertEqual(events[1], ('write',))
            self.assertEqual(unit.read_text(), ORIGINAL)
            self.assertFalse(any('swapoff' in event for event in events))


if __name__ == '__main__':
    unittest.main()
