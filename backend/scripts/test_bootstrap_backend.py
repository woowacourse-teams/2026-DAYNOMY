import hashlib
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch
import bootstrap_backend as bootstrap


class BootstrapTest(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory(); self.addCleanup(self.temp.cleanup)
        self.root=Path(self.temp.name)
        self.app=self.root/'app'; self.app.mkdir()
        self.systemd=self.root/'systemd'; self.systemd.mkdir()
        self.site=self.root/'daynomy-site.conf'; self.site.write_text('original nginx')
        self.alloy=self.root/'config.alloy'; self.alloy.write_text('matches = "_SYSTEMD_UNIT=daynomy.service"')
        self.unit=self.systemd/'daynomy.service'; self.unit.write_text('original unit')
        self.old=self.app/'old.jar'; self.old.write_bytes(b'original immutable release')
        self.artifact=self.app/'new.jar'; self.artifact.write_bytes(b'new release')
        self.calls=[]
        p=patch.object(bootstrap,'idle_original_jobs');p.start();self.addCleanup(p.stop)
        for target,name,value in [
            (bootstrap,'BOOT_HELPER',self.root/'boot/deploy_backend.py'),(bootstrap,'SYSTEMD',self.systemd),(bootstrap,'SITE',self.site),(bootstrap,'ALLOY',self.alloy),
            (bootstrap.deployment,'APP',self.app),(bootstrap.deployment,'STATE',self.app/'deployment'),
            (bootstrap.deployment,'ROUTE',self.root/'upstream.conf')]:
            p=patch.object(target,name,value); p.start(); self.addCleanup(p.stop)
        for target,name in [(bootstrap.shutil,'chown'),(bootstrap.deployment,'probe'),
                            (bootstrap.deployment,'proxy_probe'),(bootstrap.time,'sleep')]:
            p=patch.object(target,name); p.start(); self.addCleanup(p.stop)
        p=patch.object(bootstrap,'preflight',return_value=(self.old,self.site.read_text()));p.start();self.addCleanup(p.stop)
        p=patch.object(bootstrap.deployment,'available',return_value=1024**3);p.start();self.addCleanup(p.stop)
        p=patch.object(bootstrap.deployment,'command',side_effect=self.command);p.start();self.addCleanup(p.stop)

    def command(self,*args):
        self.calls.append(args)
        return ''

    def apply(self):
        bootstrap.apply(self.artifact,hashlib.sha256(self.old.read_bytes()).hexdigest())

    def test_install_preserves_original_release_and_routes_both_slots_logs(self):
        self.apply()
        self.assertEqual(self.old.read_bytes(),b'original immutable release')
        self.assertIn('SyslogIdentifier=daynomy',self.unit.read_text())
        self.assertIn('MemoryMax=800M',(self.systemd/'daynomy-backend.slice').read_text())
        self.assertIn('SYSLOG_IDENTIFIER=daynomy',self.alloy.read_text())
        self.assertEqual((self.app/'deployment/active-slot').read_text(),'blue\n')

    def test_failed_new_app_restores_unit_nginx_alloy_and_starts_original(self):
        attempts=0
        def fail_once(*args):
            nonlocal attempts
            if args==('systemctl','start','daynomy'):
                attempts+=1
                if attempts==1:raise RuntimeError('Injected start failure')
            return self.command(*args)
        bootstrap.deployment.command.side_effect=fail_once
        with self.assertRaisesRegex(RuntimeError,'Injected start failure'):self.apply()
        self.assertEqual(self.unit.read_text(),'original unit')
        self.assertEqual(self.site.read_text(),'original nginx')
        self.assertEqual(self.alloy.read_text(),'matches = "_SYSTEMD_UNIT=daynomy.service"')
        self.assertEqual(attempts,2)
        self.assertFalse(bootstrap.deployment.ROUTE.exists())
        self.assertFalse((self.systemd/'daynomy-green.service').exists())

    def test_monitor_reload_failure_does_not_prevent_original_app_recovery(self):
        def fail_reload(*args):
            if args==('systemctl','reload','alloy'):raise RuntimeError('Injected monitor failure')
            return self.command(*args)
        bootstrap.deployment.command.side_effect=fail_reload
        with self.assertRaisesRegex(RuntimeError,'inspect recovery commands'):self.apply()
        self.assertEqual(self.unit.read_text(),'original unit')
        self.assertIn(('systemctl','start','daynomy'),self.calls)
        self.assertIn(('systemctl','reload','nginx'),self.calls)

    def test_busy_original_batch_is_rejected_without_stopping_original(self):
        bootstrap.idle_original_jobs.side_effect=RuntimeError('Original scheduled jobs are not confirmed idle')
        with self.assertRaisesRegex(RuntimeError,'not confirmed idle'):self.apply()
        self.assertFalse(self.calls)
        self.assertFalse((self.app/'deployment').exists())
        self.assertEqual(self.unit.read_text(),'original unit')

if __name__=='__main__':unittest.main()
