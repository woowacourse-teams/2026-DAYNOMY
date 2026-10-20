import assert from 'node:assert/strict';
import { chmodSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { spawnSync } from 'node:child_process';
import test from 'node:test';

const workflow = readFileSync(
  new URL('../../.github/workflows/k6-dev.yml', import.meta.url),
  'utf8',
);
const python = /python3 - <<'PY'\n([\s\S]*?)\n          PY/
  .exec(workflow)[1]
  .split('\n')
  .map((line) => line.slice(10))
  .join('\n');

test('Actions passes the league fixture and run tag, and blocks global recalculation on shared dev', () => {
  const directory = mkdtempSync(join(tmpdir(), 'league-workflow-'));
  try {
    const output = join(directory, 'arguments.json');
    const executable = join(directory, 'k6');
    writeFileSync(
      executable,
      '#!/usr/bin/env python3\nimport json,os,sys\nopen(os.environ["ARGUMENTS_FILE"],"w").write(json.dumps(sys.argv[1:]))\n',
    );
    chmodSync(executable, 0o700);
    writeFileSync(join(directory, 'accounts.json'), '[]');
    writeFileSync(join(directory, 'accounts-league.json'), '{}');
    const run = (script, params) =>
      spawnSync('python3', ['-c', python], {
        cwd: directory,
        encoding: 'utf8',
        env: {
          ...process.env,
          PATH: `${directory}:${process.env.PATH}`,
          ARGUMENTS_FILE: output,
          K6_PARAMS_JSON: JSON.stringify(params),
          PORTFOLIO_TEST: 'false',
          K6_SCRIPT_NAME: script,
          BASE_URL: 'https://dev.example.com',
          GITHUB_RUN_ID: '123',
          GITHUB_RUN_ATTEMPT: '1',
        },
      });
    for (const version of ['v1', 'v2']) {
      assert.equal(
        run(`league-${version}.js`, { CASE: 'browse', EXPECTED_CONFIRMED: false }).status,
        0,
      );
      const args = JSON.parse(readFileSync(output, 'utf8'));
      assert.ok(args.includes('ACCOUNTS_FILE=./accounts-league.json'));
      assert.ok(!args.includes('ACCOUNTS_FILE=./accounts.json'));
      assert.ok(args.includes('EXPECTED_CONFIRMED=false'));
      assert.ok(args.includes('testid=123-1'));
    }
    assert.equal(run('portfolio-analysis-v1.js', {}).status, 0);
    assert.ok(JSON.parse(readFileSync(output, 'utf8')).includes('ACCOUNTS_FILE=./accounts.json'));
    for (const value of ['recalculate', 'mixed-with-recalculate']) {
      const result = run('league-v1.js', { CASE: value, ISOLATED: true });
      assert.notEqual(result.status, 0);
      assert.match(result.stderr, /separate backend\/DB clone/);
    }
    assert.notEqual(run('league-v1.js', { ACCOUNTS_FILE: './other.json' }).status, 0);
    assert.ok(workflow.includes('LEAGUE_ACCOUNTS_JSON_GZ_BASE64'));
    assert.ok(workflow.includes('trap \'rm -f "$accounts_file"\' EXIT'));
  } finally {
    rmSync(directory, { recursive: true, force: true });
  }
});
