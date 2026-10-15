import copy
import importlib.util
import json
import os
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import Mock, patch

ROOT = Path(__file__).resolve().parent.parent
SPEC = importlib.util.spec_from_file_location("loadtest_runner", ROOT / "run-loadtest.py")
runner = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(runner)


class LoadtestRunnerTest(unittest.TestCase):
    def setUp(self):
        self.config = json.loads((ROOT / "loadtest-config.json").read_text())

    def load(self, **changes):
        config = copy.deepcopy(self.config)
        config.update(changes)
        with tempfile.TemporaryDirectory() as directory:
            file = Path(directory) / "config.json"
            file.write_text(json.dumps(config))
            return runner.load_config(file)

    def test_calendar_months_and_stage_budget(self):
        config, date = self.load(referenceDate="2026-03-31")
        self.assertEqual(date, "2026-02-28")
        self.assertEqual([round(r * config["stageSeconds"]) for r in config["rpsSteps"]], [12, 24, 60, 120, 240, 360])
        _, date = self.load(tradeMonthsAgo=6)
        self.assertEqual(date, "2026-04-15")

    def test_minimal_config_derives_monitor_defaults_and_preparation_paths(self):
        config, _ = self.load()
        self.assertNotIn("namespace", config)
        self.assertNotIn("envFile", config)
        self.assertEqual(config["monitoring"]["pollSeconds"], 15)
        self.assertEqual(config["monitoring"]["hostInstances"],
                         [config["monitoring"]["backendInstance"], config["monitoring"]["databaseInstance"]])
        with patch.object(runner.subprocess, "run", return_value=Mock(returncode=0)) as process:
            runner.preparation(config, "tokens")
        command = process.call_args.args[0]
        arguments = runner.shlex.split(command[-1].removeprefix("--args="))
        self.assertEqual(arguments[arguments.index("--namespace") + 1], "lt496-20261015")
        self.assertEqual(arguments[arguments.index("--snapshot") + 1], "baseline.json")
        self.assertEqual(arguments[arguments.index("--env-file") + 1], str(runner.LOCAL / "dev.env"))
        self.assertIn("--token-seconds", arguments)

    def test_refuses_prod_duplicate_rates_path_escape_and_account_overlap(self):
        for changes in [dict(baseUrl="https://daynomy.com"), dict(rpsSteps=[1, 1]),
                        dict(rpsSteps=[5]), dict(envFile=".local/../../public.env"), dict(stageSeconds=0)]:
            with self.subTest(changes=changes), self.assertRaises(runner.StopRun):
                self.load(**changes)

    def test_monitor_refuses_missing_or_stale_series(self):
        monitor = runner.Monitor(self.config["monitoring"])
        for data in [[], [{"metric": {"instance": "host"}, "value": [0, "1"]}]]:
            class Response:
                def __enter__(self):
                    import io
                    return io.StringIO(json.dumps({"status": "success", "data": {"resultType": "vector", "result": data}}))
                def __exit__(self, *args):
                    pass
            with patch.object(monitor.opener, "open", return_value=Response()), self.assertRaises(runner.StopRun):
                monitor.vector("up", ["host"])

    def test_monitor_tracks_sustained_limit_per_host_and_clears_on_recovery(self):
        monitor = runner.Monitor(self.config["monitoring"])
        state = {"cpu": 0.9}
        def values(query, instances, fresh=True):
            if query.startswith("1-avg"):
                return {h: state["cpu"] for h in instances}
            if "MemAvailable" in query:
                return {h: 50 for h in instances}
            if "MemTotal" in query:
                return {h: 100 for h in instances}
            if "pending" in query:
                return {h: 0 for h in instances}
            return {h: 1 for h in instances}
        with patch.object(monitor, "vector", side_effect=values), patch.object(runner.time, "monotonic", return_value=1):
            self.assertFalse(monitor.sample()["quiet"])
        with patch.object(monitor, "vector", side_effect=values), patch.object(runner.time, "monotonic", return_value=62):
            with self.assertRaises(runner.StopRun):
                monitor.sample()
        state["cpu"] = 0.1
        with patch.object(monitor, "vector", side_effect=values):
            self.assertTrue(monitor.sample()["quiet"])
            self.assertEqual(monitor.exceeded_since, {})

    def test_offline_plan_never_constructs_monitor_or_prepares_database(self):
        with patch.object(runner.sys, "argv", ["run-loadtest.py", "--plan"]), \
             patch.object(runner, "Monitor") as monitor, patch.object(runner, "preparation") as preparation, \
             patch("builtins.print"):
            runner.main()
        monitor.assert_not_called()
        preparation.assert_not_called()

    def test_failed_stage_prevents_next_restore_and_leaves_recovery_marker(self):
        config = copy.deepcopy(self.config)
        config["rpsSteps"] = [0.1, 0.2]
        report = {"config": config, "fixtureHash": "a" * 64, "version": "v1", "tradeDate": "2026-09-15",
                  "rps": 0.1, "expected": 12, "started": 12, "successful": 12, "errors": 0,
                  "droppedIterations": 0, "latency": {"p(95)": 4000}, "accountsUsed": 12,
                  "k6ExitCode": 99, "outcome": "target_exceeded"}
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            local = root / ".local"
            lock = local / "execution.lock"
            monitor = Mock()
            with patch.multiple(runner, ROOT=root, LOCAL=local, LOCK=lock), \
                 patch.object(runner, "preparation") as prepare, \
                 patch.object(runner, "run_stage", return_value=report) as stage, patch("builtins.print"):
                with self.assertRaises(runner.StopRun):
                    runner.execute(config, root / "config.json", monitor)
                self.assertEqual([call.args[1] for call in prepare.call_args_list], ["check", "restore", "tokens"])
                stage.assert_called_once()
                self.assertEqual(json.loads(lock.read_text())["status"], "recovery_required")
                frozen_file = stage.call_args.args[1]
                self.assertEqual(json.loads(frozen_file.read_text()), config)
                with self.assertRaises(runner.StopRun):
                    runner.execute(config, root / "config.json", monitor)

    def test_monitor_failure_never_restores_or_starts_load(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            local = root / ".local"
            monitor = Mock()
            monitor.wait_quiet.side_effect = runner.StopRun("monitor unavailable")
            with patch.multiple(runner, ROOT=root, LOCAL=local, LOCK=local / "execution.lock"), \
                 patch.object(runner, "preparation") as prepare, patch.object(runner, "run_stage") as stage, \
                 patch("builtins.print"):
                with self.assertRaises(runner.StopRun):
                    runner.execute(self.config, root / "config.json", monitor)
                prepare.assert_not_called()
                stage.assert_not_called()


class ActionsRunnerTest(unittest.TestCase):
    def test_params_select_version_month_rate_and_duration_without_overriding_infrastructure(self):
        action, config, date = runner.actions_configuration(json.dumps({"ACTION": "execute",
            "REFERENCE_DATE": "2026-03-31", "TRADE_MONTHS_AGO": 1, "RPS_STEPS": [0.5, 1], "STAGE_SECONDS": 300}),
            {"K6_SCRIPT": "backend/loadtest/transactions-v2.js", "BASE_URL": "https://dev.daynomy.com", "PORTFOLIO_PROMETHEUS_URL": "http://127.0.0.1:9090",
             "K6_PROMETHEUS_RW_SERVER_URL": "http://127.0.0.1:9090/api/v1/write"})
        self.assertEqual((action, config["version"], date), ("execute", "v2", "2026-02-28"))
        self.assertEqual(config["rpsSteps"], [0.5, 1])
        self.assertEqual(config["stageSeconds"], 300)
        self.assertEqual(config["vus"], 50)
        for params in [{"ACTION": "seed"}, {"BASE_URL": "https://prod.example.com"}, {"RPS_STEPS": [5]},
                       {"RPS_STEPS": [1, 2, 3], "STAGE_SECONDS": 3600}, {"VERSION": "v3"}, []]:
            with self.subTest(params=params), self.assertRaises(runner.StopRun):
                runner.actions_configuration(json.dumps(params), {"K6_SCRIPT": "backend/loadtest/transactions-v1.js", "BASE_URL": "https://dev.daynomy.com", "K6_PROMETHEUS_RW_SERVER_URL": "http://127.0.0.1:9090/api/v1/write"})

    def test_workflow_filename_selects_version_and_params_cannot_override_it(self):
        for version in ("v1", "v2"):
            env = {"K6_SCRIPT": f"backend/loadtest/transactions-{version}.js"}
            _, config, _ = runner.actions_configuration("{}", env)
            self.assertEqual(config["version"], version)
            with self.assertRaises(runner.StopRun):
                runner.actions_configuration('{"VERSION":"v2"}', env)
        with self.assertRaises(runner.StopRun):
            runner.actions_configuration("{}", {"K6_SCRIPT": "backend/loadtest/support/scenario.js"})

    def test_default_plan_does_not_attach_state_or_access_monitor_db_or_k6(self):
        with tempfile.TemporaryDirectory() as folder:
            env = {"K6_SCRIPT": "backend/loadtest/transactions-v2.js", "K6_PARAMS_JSON": '{"TRADE_MONTHS_AGO":6}',
                   "PORTFOLIO_LOADTEST_RESULTS_DIR": folder}
            with patch.dict(os.environ, env, clear=True), patch.object(sys, "argv", ["run-loadtest.py", "--actions"]), \
                 patch.object(runner, "attach_state") as state, patch.object(runner, "Monitor") as monitor, \
                 patch.object(runner, "execute") as execute, patch("builtins.print"):
                runner.main()
            state.assert_not_called()
            monitor.assert_not_called()
            execute.assert_not_called()
            self.assertEqual(json.loads((Path(folder) / "request.json").read_text())["outcome"], "plan_only")

    def test_state_survives_cleanup_but_credentials_are_removed_and_runner_identity_is_checked(self):
        with tempfile.TemporaryDirectory() as folder:
            base = Path(folder)
            root = base / "checkout/backend/loadtest"
            root.mkdir(parents=True)
            env = {"K6_SCRIPT": "backend/loadtest/transactions-v1.js", "GITHUB_WORKSPACE": str(base / "checkout"), "RUNNER_NAME": "monitoring-k6-runner",
                   "PORTFOLIO_LOADTEST_STATE_DIR": str(base / "private-state")}
            with patch.object(runner, "ROOT", root):
                state = runner.attach_state(env)
                for name in ("dev.env", "users.json", "warmup-users.json", "baseline.json", "execution.lock"):
                    (state / name).write_text("private")
                runner.cleanup(env)
                self.assertFalse((root / ".local").exists())
                self.assertEqual({p.name for p in state.iterdir()}, {"baseline.json", "execution.lock", "runner.json"})
                self.assertEqual(runner.attach_state(env), state)
                runner.cleanup(env)
                with self.assertRaises(runner.StopRun):
                    runner.attach_state({**env, "RUNNER_NAME": "different-runner"})
                with self.assertRaises(runner.StopRun):
                    runner.attach_state({**env, "PORTFOLIO_LOADTEST_STATE_DIR": str(root / "state")})

    def test_failed_actions_execution_keeps_recovery_marker_across_next_run_and_exports_reports_only(self):
        with tempfile.TemporaryDirectory() as folder:
            base = Path(folder)
            root = base / "checkout/backend/loadtest"
            root.mkdir(parents=True)
            (root / "loadtest-config.json").write_text((ROOT / "loadtest-config.json").read_text())
            local = root / ".local"
            state = base / "state"
            state.mkdir()
            (state / "baseline.json").write_text("private baseline")
            env = {"K6_SCRIPT": "backend/loadtest/transactions-v1.js", "GITHUB_WORKSPACE": str(base / "checkout"), "RUNNER_NAME": "monitoring-k6-runner",
                   "PORTFOLIO_LOADTEST_STATE_DIR": str(state), "PORTFOLIO_LOADTEST_RESULTS_DIR": str(base / "published"),
                   "K6_PARAMS_JSON": '{"ACTION":"execute","RPS_STEPS":[0.1,0.2]}',
                   "BASE_URL": "https://dev.daynomy.com", "PORTFOLIO_LOADTEST_DEV_ENV": "JWT_SECRET=private-secret",
                   "K6_PROMETHEUS_RW_SERVER_URL": "http://127.0.0.1:9090/api/v1/write"}
            config = json.loads((root / "loadtest-config.json").read_text())
            report = {"config": config, "fixtureHash": "a" * 64, "version": "v1", "tradeDate": "2026-09-15",
                      "rps": 0.1, "expected": 12, "started": 12, "successful": 12, "errors": 0,
                      "droppedIterations": 0, "latency": {"p(95)": 4000}, "accountsUsed": 12,
                      "k6ExitCode": 99, "outcome": "target_exceeded"}
            with patch.object(runner, "ROOT", root), patch.multiple(runner, ROOT=root, LOCAL=local, LOCK=local / "execution.lock"), \
                 patch.dict(os.environ, env, clear=True), patch.object(sys, "argv", ["run-loadtest.py", "--actions"]), \
                 patch.object(runner, "Monitor"), patch.object(runner, "preparation") as prepare, \
                 patch.object(runner, "run_stage", return_value=report) as stage, patch("builtins.print"):
                with self.assertRaises(runner.StopRun):
                    runner.main()
                self.assertEqual([c.args[1] for c in prepare.call_args_list], ["check", "restore", "tokens"])
                self.assertEqual(json.loads((state / "execution.lock").read_text())["status"], "recovery_required")
                self.assertFalse(local.exists())
                self.assertFalse((state / "dev.env").exists())
                with self.assertRaises(runner.StopRun):
                    runner.main()
                stage.assert_called_once()
                published = base / "published"
                self.assertEqual({p.name for p in published.iterdir()}, {"request.json", "results.json", "config.json", "comparison.csv"})
                for file in published.iterdir():
                    self.assertNotIn("private-secret", file.read_text())
                    self.assertNotIn("private baseline", file.read_text())

    def test_missing_baseline_never_captures_or_restores_automatically(self):
        with tempfile.TemporaryDirectory() as folder:
            env = {"K6_SCRIPT": "backend/loadtest/transactions-v2.js", "K6_PARAMS_JSON": '{"ACTION":"execute"}', "BASE_URL": "https://dev.daynomy.com",
                   "K6_PROMETHEUS_RW_SERVER_URL": "http://127.0.0.1:9090/api/v1/write",
                   "PORTFOLIO_LOADTEST_RESULTS_DIR": folder}
            with patch.dict(os.environ, env, clear=True), patch.object(sys, "argv", ["run-loadtest.py", "--actions"]), \
                 patch.object(runner, "attach_state", return_value=Path(folder)), patch.object(runner, "cleanup"), \
                 patch.object(runner, "preparation") as preparation, patch.object(runner, "execute") as execute:
                with self.assertRaisesRegex(runner.StopRun, "Missing original baseline"):
                    runner.main()
                preparation.assert_not_called()
                execute.assert_not_called()

    def test_capture_and_manual_recovery_reuse_original_preparation_without_starting_k6(self):
        with tempfile.TemporaryDirectory() as folder:
            base = Path(folder)
            root = base / "checkout/backend/loadtest"
            root.mkdir(parents=True)
            (root / "loadtest-config.json").write_text((ROOT / "loadtest-config.json").read_text())
            local = root / ".local"
            state = base / "state"
            env = {"K6_SCRIPT": "backend/loadtest/transactions-v1.js", "GITHUB_WORKSPACE": str(base / "checkout"), "RUNNER_NAME": "monitoring-k6-runner",
                   "PORTFOLIO_LOADTEST_STATE_DIR": str(state), "PORTFOLIO_LOADTEST_RESULTS_DIR": str(base / "published"),
                   "BASE_URL": "https://dev.daynomy.com", "PORTFOLIO_LOADTEST_DEV_ENV": "JWT_SECRET=private-secret"}
            def preparation(_config, command):
                if command == "capture":
                    (state / "baseline.json").write_text("original baseline")
            with patch.object(runner, "ROOT", root), patch.multiple(runner, ROOT=root, LOCAL=local, LOCK=local / "execution.lock"), \
                 patch.dict(os.environ, env, clear=True), patch.object(sys, "argv", ["run-loadtest.py", "--actions"]), \
                 patch.object(runner, "Monitor") as monitor, patch.object(runner, "run_stage") as stage, \
                 patch.object(runner, "preparation", side_effect=preparation) as prepare, patch("builtins.print"):
                os.environ["K6_PARAMS_JSON"] = '{"ACTION":"capture"}'
                runner.main()
                self.assertEqual(prepare.call_args.args[1], "capture")
                (state / "execution.lock").write_text('{"status":"recovery_required"}')
                os.environ["K6_PARAMS_JSON"] = '{"ACTION":"acknowledge-recovery"}'
                runner.main()
                self.assertEqual([c.args[1] for c in prepare.call_args_list], ["capture", "check"])
                self.assertEqual(monitor.return_value.wait_quiet.call_count, 2)
                self.assertFalse((state / "execution.lock").exists())
                self.assertEqual((state / "baseline.json").read_text(), "original baseline")
                stage.assert_not_called()

    def test_cancellation_keeps_persistent_marker_and_exports_partial_results(self):
        config = json.loads((ROOT / "loadtest-config.json").read_text())
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            local = root / ".local"
            with patch.multiple(runner, ROOT=root, LOCAL=local, LOCK=local / "execution.lock"), \
                 patch.object(runner, "preparation"), patch.object(runner, "run_stage", side_effect=KeyboardInterrupt), \
                 patch("builtins.print"):
                with self.assertRaises(KeyboardInterrupt):
                    runner.execute(config, root / "config.json", Mock(), artifacts=root / "published")
            marker = json.loads((local / "execution.lock").read_text())
            self.assertEqual(marker["status"], "recovery_required")
            self.assertEqual(marker["reason"], "KeyboardInterrupt")
            partial = json.loads((root / "published/results.json").read_text())
            self.assertEqual(partial["stages"], [])
            self.assertEqual(partial["stopReason"], "KeyboardInterrupt")

    def test_remote_write_does_not_replace_script_tags_or_pass_signing_secret_and_workload_overrides_to_k6(self):
        config = json.loads((ROOT / "loadtest-config.json").read_text())
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            directory = root / ".local/results/run-test"
            directory.mkdir(parents=True)
            (directory / "rps-0.1.json").write_text('{"outcome":"exploratory_pass"}')
            env = {"PORTFOLIO_LOADTEST_REMOTE_WRITE": "true", "K6_RATE": "9999", "K6_DURATION": "1h",
                   "K6_PROMETHEUS_RW_SERVER_URL": "http://127.0.0.1:9090/api/v1/write",
                   "PORTFOLIO_LOADTEST_DEV_ENV": "JWT_SECRET=private-secret", "JWT_SECRET": "private-secret"}
            child = Mock(returncode=0)
            child.poll.return_value = 0
            with patch.dict(os.environ, env, clear=True), patch.object(runner, "ROOT", root), \
                 patch.object(runner.subprocess, "Popen", return_value=child) as process:
                runner.run_stage(config, root / "config.json", 0.1, directory, Mock())
            command = process.call_args.args[0]
            self.assertIn("experimental-prometheus-rw", command)
            self.assertEqual(command[-1], "transactions-v1.js")
            self.assertNotIn("--tag", command)
            child_env = process.call_args.kwargs["env"]
            self.assertEqual(child_env["LOADTEST_ID"], "run-test")
            self.assertIn("K6_PROMETHEUS_RW_SERVER_URL", child_env)
            for key in ("K6_RATE", "K6_DURATION", "PORTFOLIO_LOADTEST_DEV_ENV", "JWT_SECRET"):
                self.assertNotIn(key, child_env)


if __name__ == "__main__":
    unittest.main()
