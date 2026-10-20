import assert from 'node:assert/strict';
import {
  chmodSync,
  existsSync,
  mkdirSync,
  mkdtempSync,
  readFileSync,
  rmSync,
  writeFileSync,
} from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { spawnSync } from 'node:child_process';
import test from 'node:test';
import { gzipSync } from 'node:zlib';

const workflow = readFileSync(
  new URL('../../.github/workflows/k6-dev.yml', import.meta.url),
  'utf8',
);
const shell = /- name: Run k6 test[\s\S]*?        run: \|\n([\s\S]*?)(?=\n      - name:)/
  .exec(workflow)[1]
  .split('\n')
  .map((line) => line.slice(10))
  .join('\n');

test('unchanged Actions passes the league fixture path and run tag while preserving analysis account handling', () => {
  const directory = mkdtempSync(join(tmpdir(), 'league-workflow-'));
  try {
    const workspace = join(directory, 'checkout');
    const loadtest = join(workspace, 'loadtest');
    mkdirSync(loadtest, { recursive: true });
    const fixture = join(directory, 'accounts-league.json');
    writeFileSync(fixture, '{}');
    for (const name of ['league-v1.js', 'league-v2.js', 'portfolio-analysis-v1.js'])
      writeFileSync(join(loadtest, name), '');
    const output = join(directory, 'arguments.json');
    const executable = join(directory, 'k6');
    writeFileSync(
      executable,
      '#!/usr/bin/env python3\nimport json,os,sys\n' +
        'result={"args":sys.argv[1:],"analysisAccountsLoaded":os.path.isfile("accounts.json"),' +
        '"secretsPresent":any(key in os.environ for key in ["K6_ACCOUNTS_JSON_GZ_BASE64","PORTFOLIO_ACCOUNTS_JSON_GZ_BASE64"])}\n' +
        'open(os.environ["ARGUMENTS_FILE"],"w").write(json.dumps(result))\n',
    );
    chmodSync(executable, 0o700);
    const run = (script, params) =>
      spawnSync('bash', ['-c', shell], {
        cwd: workspace,
        encoding: 'utf8',
        env: {
          ...process.env,
          PATH: `${directory}:${process.env.PATH}`,
          ARGUMENTS_FILE: output,
          K6_PARAMS_JSON: JSON.stringify(params),
          K6_SCRIPT: `loadtest/${script}`,
          K6_ACCOUNTS_JSON_GZ_BASE64: gzipSync('[]').toString('base64'),
          PORTFOLIO_ACCOUNTS_JSON_GZ_BASE64: '',
          BASE_URL: 'https://dev.example.com',
          K6_PROMETHEUS_RW_SERVER_URL: 'http://prometheus.example.com/api/v1/write',
          GITHUB_WORKSPACE: workspace,
          GITHUB_ACTIONS: 'true',
          GITHUB_RUN_ID: '123',
          GITHUB_RUN_ATTEMPT: '1',
          LOADTEST_ID: '123-1',
        },
      });
    for (const version of ['v1', 'v2']) {
      const result = run(`league-${version}.js`, {
        CASE: 'browse',
        EXPECTED_CONFIRMED: false,
        LEAGUE_FIXTURE_FILE: fixture,
      });
      assert.equal(result.status, 0, result.stderr);
      const { args, analysisAccountsLoaded, secretsPresent } = JSON.parse(
        readFileSync(output, 'utf8'),
      );
      assert.ok(args.includes(`LEAGUE_FIXTURE_FILE=${fixture}`));
      assert.ok(!args.some((arg) => arg.startsWith('ACCOUNTS_FILE=')));
      assert.ok(args.includes('BASE_URL=https://dev.example.com'));
      assert.ok(args.includes('EXPECTED_CONFIRMED=false'));
      assert.ok(args.includes('testid=123-1'));
      assert.equal(args.at(-1), `league-${version}.js`);
      assert.equal(analysisAccountsLoaded, false);
      assert.equal(secretsPresent, false);
      assert.ok(existsSync(fixture));
    }
    assert.equal(run('portfolio-analysis-v1.js', {}).status, 0);
    const analysis = JSON.parse(readFileSync(output, 'utf8'));
    assert.ok(analysis.args.includes('ACCOUNTS_FILE=./accounts.json'));
    assert.equal(analysis.analysisAccountsLoaded, true);
    assert.equal(analysis.secretsPresent, false);
    assert.ok(!existsSync(join(loadtest, 'accounts.json')), 'analysis accounts are cleaned up');
    for (const key of ['BASE_URL', 'ACCOUNTS_FILE']) {
      const result = run('league-v1.js', { [key]: 'unmanaged' });
      assert.notEqual(result.status, 0, key);
      assert.match(result.stderr, /managed by the workflow/);
    }
    assert.notEqual(run('missing.js', {}).status, 0);
  } finally {
    rmSync(directory, { recursive: true, force: true });
  }
});
