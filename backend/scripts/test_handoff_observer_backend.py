import os
import tempfile
import unittest
from pathlib import Path
from unittest.mock import Mock, patch
from ownership_handoff_lab import HandoffLab


class HandoffObserverTest(unittest.TestCase):
    def test_observer_matches_output_owner_and_preserves_diagnostics(self):
        with tempfile.TemporaryDirectory() as folder:
            output=Path(folder)
            lab=HandoffLab(Mock(return_value=''),'test','app','nginx','volume',output)
            with patch('ownership_handoff_lab.subprocess.Popen') as process, patch('ownership_handoff_lab.threading.Thread'), patch('ownership_handoff_lab.subprocess.run'):
                lab.start(300)
                args=process.call_args.args[0]
                self.assertEqual(args[args.index('--user')+1],str(os.getuid()))
                stream=process.call_args.kwargs['stdout']
                stream.write('observer error retained\n');stream.flush()
                lab.cleanup()
                self.assertTrue(stream.closed)
            self.assertEqual((output/'route-k6.log').read_text(),'observer error retained\n')


if __name__=='__main__':unittest.main()
