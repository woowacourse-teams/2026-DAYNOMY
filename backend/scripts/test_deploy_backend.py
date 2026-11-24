import importlib.util
import json
import tempfile
import unittest
import zipfile
from pathlib import Path
from unittest.mock import patch

spec = importlib.util.spec_from_file_location('deploy', Path(__file__).with_name('deploy_backend.py'))
deploy = importlib.util.module_from_spec(spec)
spec.loader.exec_module(deploy)


class DeploymentTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.app = Path(self.temp.name)
        self.state = self.app / 'deployment'
        self.state.mkdir()
        (self.app / 'releases').mkdir()
        self.route = self.app / 'upstream.conf'
        self.route.write_text(deploy.route('blue'))
        (self.state / 'active-slot').write_text('blue\n')
        self.old = self.jar('old.jar')
        self.artifact = self.jar('new.jar')
        (self.state / 'blue.jar').symlink_to(self.old)
        self.calls = []
        for name, value in [('APP', self.app), ('STATE', self.state), ('ROUTE', self.route)]:
            p = patch.object(deploy, name, value)
            p.start(); self.addCleanup(p.stop)
        for name in ('probe', 'proxy_probe', 'score', 'candidate_override'):
            p = patch.object(deploy, name)
            p.start(); self.addCleanup(p.stop)
        for name, value in [('is_active', False), ('available', 1024**3), ('nginx_workers', {'111': '222'}), ('wait_workers', True)]:
            p = patch.object(deploy, name, return_value=value)
            p.start(); self.addCleanup(p.stop)
        p = patch.object(deploy.time, 'sleep'); p.start(); self.addCleanup(p.stop)
        p = patch.object(deploy, 'command', side_effect=self.command); p.start(); self.addCleanup(p.stop)

    def command(self, *args):
        self.calls.append(args)
        return ''

    def jar(self, name, sql='select 1'):
        path = self.app / name
        with zipfile.ZipFile(path, 'w') as archive:
            archive.writestr('BOOT-INF/classes/org/grit/daynomy/deployment/DeploymentSchedulingConfig.class', b'fixture')
            archive.writestr('BOOT-INF/classes/db/migration/V1__schema.sql', sql)
        return path

    def test_success_switches_both_proxies_and_scheduler_owner_then_retires_old(self):
        deploy.deploy(self.artifact, 'a' * 40)
        self.assertEqual((self.state / 'active-slot').read_text(), 'green\n')
        self.assertIn('127.0.0.1:8082', self.route.read_text())
        self.assertIn('127.0.0.1:8182', self.route.read_text())
        self.assertIn(('systemctl', 'stop', 'daynomy'), self.calls)
        self.assertTrue(json.loads((self.state / 'last-deployment.json').read_text())['active'] == 'green')

    def test_nginx_validation_failure_keeps_old_running_and_restores_route(self):
        original = self.route.read_text()
        def fail_new_configuration(*args):
            if args == ('nginx', '-t') and '127.0.0.1:8082' in self.route.read_text():
                raise RuntimeError('Injected nginx validation failure')
            return self.command(*args)
        deploy.command.side_effect = fail_new_configuration
        with self.assertRaises(RuntimeError): deploy.deploy(self.artifact, 'b' * 40)
        self.assertEqual(self.route.read_text(), original)
        self.assertEqual((self.state / 'active-slot').read_text(), 'blue\n')
        self.assertNotIn(('systemctl', 'stop', 'daynomy'), self.calls)
        self.assertIn(('systemctl', 'stop', 'daynomy-green'), self.calls)

    def test_failed_public_verification_rolls_back_before_candidate_stops(self):
        deploy.proxy_probe.side_effect = lambda slot: (_ for _ in ()).throw(RuntimeError('Injected bad proxy')) if slot == 'green' else None
        with self.assertRaises(RuntimeError): deploy.deploy(self.artifact, 'c' * 40)
        self.assertEqual((self.state / 'active-slot').read_text(), 'blue\n')
        self.assertEqual(self.route.read_text(), deploy.route('blue'))
        self.assertNotIn(('systemctl', 'stop', 'daynomy'), self.calls)

    def test_inflight_requests_keep_old_jvm_alive(self):
        deploy.wait_workers.return_value = False
        deploy.deploy(self.artifact, 'd' * 40)
        self.assertNotIn(('systemctl', 'stop', 'daynomy'), self.calls)
        self.assertTrue((self.state / 'retirement.json').exists())

    def test_rollback_also_preserves_candidate_inflight_requests(self):
        deploy.proxy_probe.side_effect = lambda slot: (_ for _ in ()).throw(RuntimeError('Injected bad proxy')) if slot == 'green' else None
        deploy.wait_workers.return_value = False
        with self.assertRaises(RuntimeError): deploy.deploy(self.artifact, 'a' * 40)
        self.assertEqual((self.state / 'active-slot').read_text(), 'blue\n')
        self.assertNotIn(('systemctl', 'stop', 'daynomy-green'), self.calls)
        self.assertEqual(json.loads((self.state/'retirement.json').read_text())['slot'], 'green')

    def test_boot_owner_restores_route_after_interrupted_cutover(self):
        self.route.write_text(deploy.route('green'))
        deploy.prepare_slot('blue')
        self.assertEqual(self.route.read_text(),deploy.route('blue'))
        self.assertIn(('systemctl','reload','nginx'),self.calls)
        self.assertTrue(deploy.owns_slot('blue'))
        self.assertFalse(deploy.owns_slot('green'))

    def test_boot_inactive_slot_does_not_change_route(self):
        original=self.route.read_text()
        deploy.prepare_slot('green')
        self.assertEqual(self.route.read_text(),original)
        self.assertFalse(self.calls)

    def test_schema_difference_is_rejected_before_start(self):
        artifact = self.jar('changed-schema.jar', 'drop table forbidden')
        with self.assertRaisesRegex(RuntimeError, 'Schema migrations differ'):
            deploy.deploy(artifact, 'e' * 40)
        self.assertFalse(self.calls)

    def test_low_memory_is_rejected_before_start(self):
        deploy.available.return_value = deploy.RESERVE
        with self.assertRaisesRegex(RuntimeError, 'Insufficient available memory'):
            deploy.deploy(self.artifact, 'f' * 40)
        self.assertFalse(self.calls)


if __name__ == '__main__': unittest.main()
