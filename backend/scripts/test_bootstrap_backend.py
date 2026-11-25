import hashlib
import io
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
        self.snippet=self.root/'analysis-snippet.conf'; self.snippet.write_text('proxy_pass http://127.0.0.1:8080;\n')
        self.alloy=self.root/'config.alloy'; self.alloy.write_text('matches = "_SYSTEMD_UNIT=daynomy.service"')
        self.unit=self.systemd/'daynomy.service'; self.unit.write_text('original unit')
        self.old=self.app/'old.jar'; self.old.write_bytes(b'original immutable release')
        self.artifact=self.app/'new.jar'; self.artifact.write_bytes(b'new release')
        self.calls=[]
        p=patch.object(bootstrap,'idle_original_jobs');p.start();self.addCleanup(p.stop)
        for name in ('candidate_override','score'):
            p=patch.object(bootstrap.deployment,name);p.start();self.addCleanup(p.stop)
        for target,name,value in [
            (bootstrap,'BOOT_HELPER',self.root/'boot/deploy_backend.py'),(bootstrap,'SYSTEMD',self.systemd),(bootstrap,'SITE',self.site),(bootstrap,'ALLOY',self.alloy),
            (bootstrap,'DEVELOPMENT_API_SNIPPET',self.snippet),
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
        # The root-owned recovery helper updates Nginx; the application remains unprivileged.
        self.assertIn('ExecCondition=+/usr/bin/python3 ',self.unit.read_text())
        self.assertIn('ExecStartPre=+/usr/bin/python3 ',self.unit.read_text())
        self.assertIn('User=ec2-user',self.unit.read_text())
        self.assertNotIn('ExecStart=+',self.unit.read_text())
        self.assertIn('MemoryMax=800M',(self.systemd/'daynomy-backend.slice').read_text())
        self.assertIn('SYSLOG_IDENTIFIER=daynomy',self.alloy.read_text())
        self.assertEqual((self.app/'deployment/active-slot').read_text(),'blue\n')

    def test_retirement_timer_installation_does_not_restart_or_route_an_app(self):
        state=self.app/'deployment';state.mkdir()
        (state/'active-slot').write_text('blue\n')
        bootstrap.BOOT_HELPER.parent.mkdir()
        with patch.object(bootstrap.deployment,'require_target_instance'):
            bootstrap.install_retirement_timer()
        self.assertEqual((state/'active-slot').read_text(),'blue\n')
        service=(self.systemd/'daynomy-retirement.service').read_text()
        self.assertIn('--environment production --retire-pending',service)
        self.assertIn('OnUnitInactiveSec=15s',(self.systemd/'daynomy-retirement.timer').read_text())
        self.assertEqual(self.calls,[('systemctl','daemon-reload'),
                                    ('systemctl','enable','--now','daynomy-retirement.timer')])

    def test_retirement_timer_refuses_an_uninitialized_or_wrong_instance(self):
        with patch.object(bootstrap.deployment,'require_target_instance',side_effect=RuntimeError('wrong instance')):
            with self.assertRaisesRegex(RuntimeError,'wrong instance'):bootstrap.install_retirement_timer()
        with patch.object(bootstrap.deployment,'require_target_instance'):
            with self.assertRaisesRegex(RuntimeError,'Bootstrap'):bootstrap.install_retirement_timer()
        self.assertEqual(self.calls,[])

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

    def test_development_analysis_snippet_switches_with_site(self):
        snippet=self.root/'analysis.conf'
        snippet.write_text('proxy_pass http://127.0.0.1:8080;\n')
        with patch.object(bootstrap.deployment,'ENVIRONMENT','development'), patch.object(bootstrap,'DEVELOPMENT_API_SNIPPET',snippet):
            self.apply()
        self.assertEqual(snippet.read_text(),'proxy_pass http://daynomy_backend;\n')

    def test_development_analysis_snippet_is_restored_on_failed_start(self):
        snippet=self.root/'analysis.conf'
        original='proxy_pass http://127.0.0.1:8080;\n'
        snippet.write_text(original)
        starts=0
        def start_failure(*args):
            nonlocal starts
            if args==('systemctl','start','daynomy'):
                starts+=1
                if starts==1:raise RuntimeError('Injected development failure')
            return self.command(*args)
        bootstrap.deployment.command.side_effect=start_failure
        with patch.object(bootstrap.deployment,'ENVIRONMENT','development'), patch.object(bootstrap,'DEVELOPMENT_API_SNIPPET',snippet):
            with self.assertRaisesRegex(RuntimeError,'Injected development failure'):self.apply()
        self.assertEqual(snippet.read_text(),original)
        self.assertEqual(self.site.read_text(),'original nginx')

    def test_monitor_reload_failure_does_not_prevent_original_app_recovery(self):
        def fail_reload(*args):
            if args==('systemctl','reload','alloy'):raise RuntimeError('Injected monitor failure')
            return self.command(*args)
        bootstrap.deployment.command.side_effect=fail_reload
        with self.assertRaisesRegex(RuntimeError,'inspect recovery commands'):self.apply()
        self.assertEqual(self.unit.read_text(),'original unit')
        self.assertIn(('systemctl','start','daynomy'),self.calls)
        self.assertIn(('systemctl','reload','nginx'),self.calls)

    def test_initial_jar_survives_runner_artifact_cleanup(self):
        self.apply()
        original=self.artifact.read_bytes()
        self.artifact.unlink()
        installed=(self.app/'deployment/blue.jar').resolve(strict=True)
        self.assertEqual(installed.parent,(self.app/'releases').resolve())
        self.assertEqual(installed.read_bytes(),original)

    def test_busy_original_batch_is_rejected_without_stopping_original(self):
        bootstrap.idle_original_jobs.side_effect=RuntimeError('Original scheduled jobs are not confirmed idle')
        with self.assertRaisesRegex(RuntimeError,'not confirmed idle'):self.apply()
        self.assertFalse(self.calls)
        self.assertFalse((self.app/'deployment').exists())
        self.assertEqual(self.unit.read_text(),'original unit')

    def test_initial_schema_change_requires_explicit_shared_state_opt_in(self):
        before={'BOOT-INF/classes/db/migration/V1__existing.sql':'original'}
        after=dict(before)
        after['BOOT-INF/classes/db/migration/V99__share_sessions_and_guest_analysis_trials.sql']='reviewed'
        with self.assertRaisesRegex(RuntimeError,'Schema migration mismatch'):
            bootstrap.validate_initial_migrations(before,after)
        bootstrap.validate_initial_migrations(before,after,True)
        after['BOOT-INF/classes/db/migration/V98__create_portfolio_transaction_requests.sql']='existing-trade'
        bootstrap.validate_initial_migrations(before,after,True)

    def test_identical_completed_development_release_starts_as_standby_green(self):
        def standby_ready(slot):
            self.assertEqual(slot,'green')
            self.assertEqual((self.app/'deployment/active-slot').read_text(),'blue\n')
        bootstrap.deployment.probe.side_effect=standby_ready
        probes=0
        def proxy_ready(slot):
            nonlocal probes
            probes+=1
            standby_ready(slot)
            if probes==1:raise RuntimeError('Previous Nginx workers still active')
        bootstrap.deployment.proxy_probe.side_effect=proxy_ready
        with patch.object(bootstrap.deployment,'ENVIRONMENT','development'), patch.object(bootstrap,'completed_development_recalculation') as completed:
            bootstrap.apply(self.old,hashlib.sha256(self.old.read_bytes()).hexdigest(),reuse_initialized_development_release=True)
        completed.assert_called_once()
        self.assertIn(('systemctl','start','daynomy-green'),self.calls)
        self.assertNotIn(('systemctl','start','daynomy'),self.calls)
        self.assertEqual((self.app/'deployment/active-slot').read_text(),'green\n')
        self.assertEqual((self.app/'deployment/green.jar').resolve(),(self.app/'deployment/blue.jar').resolve())
        self.assertEqual(bootstrap.deployment.ROUTE.read_text(),bootstrap.deployment.route('green'))
        self.assertEqual(bootstrap.deployment.candidate_override.call_args_list[0].args,('green',True))
        self.assertEqual(bootstrap.deployment.candidate_override.call_args_list[-1].args,('green',False))
        self.assertEqual(probes,2)

    def test_initialized_handover_rejects_another_jar_or_production(self):
        with patch.object(bootstrap.deployment,'ENVIRONMENT','development'):
            with self.assertRaisesRegex(RuntimeError,'identical development JAR'):
                bootstrap.apply(self.artifact,hashlib.sha256(self.old.read_bytes()).hexdigest(),reuse_initialized_development_release=True)
        with self.assertRaisesRegex(RuntimeError,'identical development JAR'):
            bootstrap.apply(self.old,hashlib.sha256(self.old.read_bytes()).hexdigest(),reuse_initialized_development_release=True)
        self.assertFalse(self.calls)

    def test_unfinished_recalculation_does_not_stop_original(self):
        with patch.object(bootstrap.deployment,'ENVIRONMENT','development'), patch.object(bootstrap,'completed_development_recalculation',side_effect=RuntimeError('not completed')):
            with self.assertRaisesRegex(RuntimeError,'not completed'):
                bootstrap.apply(self.old,hashlib.sha256(self.old.read_bytes()).hexdigest(),reuse_initialized_development_release=True)
        self.assertFalse(self.calls)
        self.assertFalse((self.app/'deployment').exists())

    def test_failed_standby_start_restores_original_and_stops_green(self):
        def fail_green(*args):
            self.calls.append(args)
            if args==('systemctl','start','daynomy-green'):
                raise RuntimeError('Injected standby start failure')
            return ''
        bootstrap.deployment.command.side_effect=fail_green
        with patch.object(bootstrap.deployment,'ENVIRONMENT','development'), patch.object(bootstrap,'completed_development_recalculation'):
            with self.assertRaisesRegex(RuntimeError,'Injected standby start failure'):
                bootstrap.apply(self.old,hashlib.sha256(self.old.read_bytes()).hexdigest(),reuse_initialized_development_release=True)
        self.assertIn(('systemctl','stop','daynomy-green'),self.calls)
        self.assertIn(('systemctl','start','daynomy'),self.calls)
        self.assertEqual(self.unit.read_text(),'original unit')
        self.assertEqual(self.site.read_text(),'original nginx')

    def test_shared_state_opt_in_never_allows_changed_or_other_migrations(self):
        before={'BOOT-INF/classes/db/migration/V1__existing.sql':'original'}
        for after in [
            {'BOOT-INF/classes/db/migration/V1__existing.sql':'changed'},
            {**before,'BOOT-INF/classes/db/migration/V99__unreviewed.sql':'other'},
            {**before,'BOOT-INF/classes/db/migration/V98__create_portfolio_transaction_requests.sql':'existing-trade'},
            {},
        ]:
            with self.assertRaisesRegex(RuntimeError,'Schema migration mismatch'):
                bootstrap.validate_initial_migrations(before,after,True)

class RecalculationCompletionTest(unittest.TestCase):
    def metrics(self,succeeded=10,target=10,failed=0,last=123,run_failed=0):
        return ('daynomy_league_calculation_latest_profiles{application="daynomy",outcome="target"} '+str(target)+'\n'
                'daynomy_league_calculation_latest_profiles{application="daynomy",outcome="succeeded"} '+str(succeeded)+'\n'
                'daynomy_league_calculation_latest_profiles{application="daynomy",outcome="failed"} '+str(failed)+'\n'
                'daynomy_league_calculation_latest_run_failed{application="daynomy"} '+str(run_failed)+'\n'
                'daynomy_league_calculation_last_completed_epoch_seconds{application="daynomy"} '+str(last)+'\n')

    def check(self,metrics):
        with patch.object(bootstrap.urllib.request,'urlopen',return_value=io.BytesIO(metrics.encode())):
            bootstrap.completed_development_recalculation()

    def test_success_requires_all_profiles_and_completed_run(self):
        self.check(self.metrics())

    def test_running_failed_empty_or_missing_metrics_are_rejected(self):
        for metrics in [self.metrics(succeeded=9),self.metrics(failed=1),self.metrics(last=0),
                        self.metrics(run_failed=1),self.metrics(target=0,succeeded=0),'']:
            with self.subTest(metrics=metrics),self.assertRaisesRegex(RuntimeError,'recalculation'):
                self.check(metrics)


class DevelopmentTargetTest(unittest.TestCase):
    def tearDown(self):
        bootstrap.configure_environment('production')

    def test_development_units_never_start_production_services(self):
        bootstrap.configure_environment('development')
        text = bootstrap.unit('green')
        self.assertIn('/deployment-dev/green.jar', text)
        self.assertIn('--environment development --owns-slot green', text)
        self.assertIn('SyslogIdentifier=daynomy-dev', text)
        self.assertIn('Slice=daynomy-backend-dev.slice', text)
        self.assertEqual(bootstrap.deployment.SLOTS['blue'][0], 'daynomy-dev')
        self.assertEqual(bootstrap.deployment.SLOTS['green'][0], 'daynomy-dev-green')
        self.assertEqual(bootstrap.deployment.PUBLIC_ORIGIN, 'https://dev.daynomy.com')
        self.assertIn('-Xmx192m',text)
        self.assertIn('-XX:+UseSerialGC',text)

    def test_failed_or_restored_memory_profile_cannot_be_installed(self):
        bootstrap.configure_environment('development')
        with tempfile.TemporaryDirectory() as folder:
            root=Path(folder);unit=root/'daynomy-dev.service'
            good='[Service]\nEnvironment=MALLOC_ARENA_MAX=2\nMemoryMax=512M\nMemorySwapMax=0\nExecStart=/java '+bootstrap.DEVELOPMENT_JVM_FLAGS+' -jar /current.jar\n'
            with patch.object(bootstrap,'SYSTEMD',root):
                for bad in [good.replace('-Xmx192m','-Xmx128m'),'[Service]\nExecStart=/java -jar /current.jar\n']:
                    unit.write_text(bad)
                    with self.assertRaisesRegex(RuntimeError,'memory profile first'):
                        bootstrap.require_development_memory_profile()
                unit.write_text(good)
                with patch.object(bootstrap.deployment,'command',side_effect=['101','0','-XX:+UseSerialGC']),patch.object(Path,'read_bytes',return_value=('java\0'+bootstrap.DEVELOPMENT_JVM_FLAGS.replace(' ','\0')+'\0-jar\0/current.jar\0').encode()):
                    bootstrap.require_development_memory_profile()
                with patch.object(bootstrap.deployment,'command',side_effect=['101','0','-XX:+UseG1GC']),patch.object(Path,'read_bytes',return_value=('java\0'+bootstrap.DEVELOPMENT_JVM_FLAGS.replace(' ','\0')+'\0').encode()):
                    with self.assertRaisesRegex(RuntimeError,'GC differs'):
                        bootstrap.require_development_memory_profile()
                with patch.object(bootstrap.deployment,'command',side_effect=['101','1']),patch.object(Path,'read_bytes',return_value=('java\0'+bootstrap.DEVELOPMENT_JVM_FLAGS.replace(' ','\0')+'\0').encode()):
                    with self.assertRaisesRegex(RuntimeError,'restarted'):
                        bootstrap.require_development_memory_profile()

    def test_development_target_rejects_a_production_instance(self):
        bootstrap.configure_environment('development')
        with patch.object(Path, 'read_text', return_value='i-0c863ab4dc205e273'):
            with self.assertRaisesRegex(RuntimeError, 'target instance'):
                bootstrap.deployment.require_target_instance()

    def test_production_target_remains_separate(self):
        bootstrap.configure_environment('development')
        development_state = bootstrap.deployment.STATE
        bootstrap.configure_environment('production')
        self.assertNotEqual(bootstrap.deployment.STATE, development_state)
        self.assertEqual(bootstrap.deployment.PUBLIC_ORIGIN, 'https://daynomy.com')
        self.assertIn('--environment production --owns-slot blue', bootstrap.unit('blue'))
        self.assertNotIn('UseSerialGC',bootstrap.unit('blue'))


if __name__=='__main__':unittest.main()
