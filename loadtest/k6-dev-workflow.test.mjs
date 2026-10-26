import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { spawnSync } from 'node:child_process';
import test from 'node:test';

const workflow = readFileSync(new URL('../.github/workflows/k6-dev.yml', import.meta.url), 'utf8');
const source = workflow.split("python3 - <<'PY'\n")[1].split('\n          PY')[0]
  .split('\n').map((line) => line.slice(10)).join('\n');

function runWorkflow(script, returnCodes, params = { RPS: 100, VUS: 50 }) {
  const harness = `
import json, os, subprocess
from unittest.mock import patch
source, script, return_codes, params = json.loads(input())
calls = []
def run(args):
    calls.append(args)
    return subprocess.CompletedProcess(args, return_codes[len(calls) - 1])
with patch.dict(os.environ, {
    "K6_SCRIPT_NAME": script, "LOADTEST_ID": "123-1", "PORTFOLIO_TEST": "false",
    "K6_PARAMS_JSON": json.dumps(params), "BASE_URL": "https://loadtest.invalid"
}), patch("subprocess.run", run), patch("os.path.isfile", return_value=False):
    try:
        exec(source)
    except SystemExit as result:
        print(json.dumps({"code": result.code, "calls": calls}))
`;
  const result = spawnSync('python3', ['-c', harness], {
    input: JSON.stringify([source, script, returnCodes, params]), encoding: 'utf8',
  });
  assert.equal(result.status, 0, result.stderr);
  return JSON.parse(result.stdout.trim().split('\n').at(-1));
}

for (const script of ['league-rankings-load.js', 'league-investor-details-load.js', 'content-search-load.js']) {
  test(`${script}: warmup metrics are separated and only the second run decides performance`, () => {
    for (const warmupExit of [0, 99]) {
      const result = runWorkflow(script, [warmupExit, 0]);
      assert.equal(result.code, 0);
      assert.equal(result.calls.length, 2);
      const [warmup, measured] = result.calls;
      assert.ok(warmup.includes('testid=123-1-warmup'));
      assert.ok(warmup.includes('run_phase=warmup'));
      assert.ok(measured.includes('testid=123-1'));
      assert.ok(measured.includes('run_phase=measurement'));
      const options = (args) => args.slice(0, args.indexOf('--tag'));
      assert.deepEqual(options(warmup), options(measured));
      assert.ok(measured.includes('RPS=100'));
      assert.ok(measured.includes('VUS=50'));
      assert.equal(warmup.at(-1), script);
      assert.equal(measured.at(-1), script);
    }
    assert.equal(runWorkflow(script, [99, 99]).code, 99);
  });

  test(`${script}: warmup setup/runtime failures stop before measurement`, () => {
    for (const code of [1, 107, -15]) {
      const result = runWorkflow(script, [code]);
      assert.equal(result.code, code);
      assert.equal(result.calls.length, 1);
    }
  });
}

test('other scripts still run once and propagate threshold failures', () => {
  const result = runWorkflow('league-rankings-smoke.js', [99]);
  assert.equal(result.code, 99);
  assert.equal(result.calls.length, 1);
});

test('invalid parameters stop before either run', () => {
  const result = runWorkflow('league-rankings-load.js', [], { BASE_URL: 'https://other.invalid' });
  assert.equal(result.calls.length, 0);
  assert.equal(result.code, 'BASE_URL and ACCOUNTS_FILE are managed by the workflow.');
});
