import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { spawnSync } from "node:child_process";
import test from "node:test";
import vm from "node:vm";

function fixture(api, count = api === "delete" ? 10 : 1000) {
  const encode = (value) =>
    Buffer.from(JSON.stringify(value)).toString("base64url");
  return {
    schemaVersion: 1,
    api,
    namespace: "lt746-fixture",
    nicknamePrefix: "m12345",
    baseUrl: "https://loadtest.invalid",
    issuer: "daynomy-test",
    accounts: Array.from({ length: count + 1 }, (_, i) => ({
      testNumber: i,
      memberId: i + 1,
      email: `test${i}@example.invalid`,
      nickname: `seed${i}`,
      name: null,
      role: "USER",
      status: "ACTIVE",
      ...(i === 0 ? { warmup: true } : {}),
      accessToken: `${encode({ alg: "HS256" })}.${encode({
        sub: String(i + 1),
        iss: "daynomy-test",
        token_type: "access",
        role: "USER",
        exp: 4102444800,
      })}.test`,
    })),
  };
}
function load(api = "get", env = {}, input = fixture(api)) {
  const calls = [],
    metrics = {};
  const state = { failure: null, deletionMaxAge: -1, csrfFailure: false };
  const db = new Map(input.accounts.map((row) => [row.memberId, { ...row }]));
  const execution = {
    scenario: { iterationInTest: 0 },
    vu: { idInTest: 1 },
    test: {
      abort: (message) => {
        throw new Error(message);
      },
    },
  };
  const context = vm.createContext({
    __ENV: {
      BASE_URL: input.baseUrl,
      API: api,
      RPS: "1",
      VUS: "1",
      DURATION: "10s",
      MEMBER_ACCOUNTS_FILE: "/private/accounts.json",
      ...env,
    },
    __ITER: 0,
    exec: execution,
    open: () => JSON.stringify(input),
    SharedArray: function (_, callback) {
      return callback();
    },
    encoding: {
      b64decode: (value) => Buffer.from(value, "base64url").toString(),
    },
    check: (response, checks) =>
      Object.values(checks).every((predicate) => predicate(response)),
    Counter: class {
      constructor(name) {
        metrics[name] = [];
        this.name = name;
      }
      add(value) {
        metrics[this.name].push(value);
      }
    },
    Rate: class {
      constructor(name) {
        metrics[name] = [];
        this.name = name;
      }
      add(value) {
        metrics[this.name].push(value);
      }
    },
    Trend: class {
      constructor(name) {
        metrics[name] = [];
        this.name = name;
      }
      add(value) {
        metrics[this.name].push(value);
      }
    },
  });
  function request(method, url, body, params) {
    calls.push({ method, url, body, params, vu: execution.vu.idInTest });
    if (url.endsWith("/csrf"))
      return {
        status: state.csrfFailure ? 403 : 200,
        cookies: { "XSRF-TOKEN": [{ value: "csrf" }] },
        json: () => ({ token: "csrf", headerName: "X-XSRF-TOKEN" }),
      };
    const claims = JSON.parse(
      Buffer.from(params.jar.cookies.access_token.split(".")[1], "base64url"),
    );
    const row = db.get(Number(claims.sub));
    const response = {
      status: 200,
      timings: { duration: 5 },
      json: () => ({
        id: row.memberId,
        email: row.email,
        name: row.name,
        nickname: row.nickname,
        role: row.role,
      }),
    };
    if (row.status !== "ACTIVE") return { ...response, status: 401 };
    if (method !== "GET") {
      assert.equal(params.jar.cookies["XSRF-TOKEN"], "csrf");
      assert.equal(params.headers["X-XSRF-TOKEN"], "csrf");
    }
    if (params.tags.phase === "target" && state.failure !== null)
      return { ...response, status: state.failure };
    if (method === "PATCH") {
      row.nickname = JSON.parse(body).nickname;
    }
    if (method === "DELETE") {
      row.status = "WITHDRAWN";
      response.status = 204;
      response.cookies = {
        access_token: [{ value: "", path: "/", max_age: state.deletionMaxAge }],
        refresh_token: [
          { value: "", path: "/api/auth", max_age: state.deletionMaxAge },
        ],
      };
    }
    return response;
  }
  context.http = {
    CookieJar: class {
      constructor() {
        this.cookies = {};
      }
      set(_, name, value) {
        this.cookies[name] = value;
      }
    },
    get: (url, params) => request("GET", url, null, params),
    patch: (url, body, params) => request("PATCH", url, body, params),
    del: (url, body, params) => request("DELETE", url, body, params),
  };
  const source = readFileSync(
    new URL("./member-v1-load.js", import.meta.url),
    "utf8",
  )
    .replace(/^import .*;\n/gm, "")
    .replace("export const options", "const options")
    .replace("export default function", "function runScenario")
    .replaceAll("export function", "function");
  vm.runInContext(
    `${source}\nglobalThis.program={options,setup,run:runScenario,teardown,handleSummary};`,
    context,
  );
  return { ...context.program, context, execution, calls, metrics, state, db };
}

test("GET distributes requests over the fixture and excludes preflight from target metrics", () => {
  const s = load();
  const data = s.setup();
  assert.equal(s.metrics.member_target_requests.length, 0);
  for (let i = 0; i < 10; i++) {
    s.execution.scenario.iterationInTest = i;
    s.run(data);
  }
  assert.equal(
    s.calls.filter((c) => c.params.tags.phase === "target").length,
    10,
  );
  assert.ok(s.metrics.member_target_valid.every(Boolean));
  s.execution.scenario.iterationInTest = 10;
  s.run(data);
  assert.equal(s.metrics.member_target_requests.length, 10);
});
test("PATCH partitions 1000 members among 150 VUs without overlapping ownership and always changes names", () => {
  const s = load("patch", { RPS: "300", VUS: "150" });
  const data = s.setup();
  const owners = new Map();
  let index = 0;
  for (let round = 0; round < 8; round++)
    for (let vu = 1; vu <= 150; vu++) {
      s.execution.vu.idInTest = vu;
      s.execution.scenario.iterationInTest = index++;
      s.context.__ITER = round;
      s.run(data);
      const call = s.calls.at(-1);
      const token = call.params.jar.cookies.access_token;
      const id = JSON.parse(Buffer.from(token.split(".")[1], "base64url")).sub;
      if (owners.has(id)) assert.equal(owners.get(id), vu);
      owners.set(id, vu);
      assert.ok(JSON.parse(call.body).nickname.length <= 20);
    }
  assert.equal(owners.size, 1000);
  assert.ok(s.metrics.member_target_valid.every(Boolean));
});
test("DELETE uses each member once, validates real k6 cookie deletion and rejects withdrawn access", () => {
  const s = load("delete");
  const data = s.setup();
  for (let i = 0; i < 10; i++) {
    s.execution.scenario.iterationInTest = i;
    s.run(data);
  }
  s.teardown();
  assert.ok([...s.db.values()].every((row) => row.status === "WITHDRAWN"));
  assert.ok(s.metrics.member_target_valid.every(Boolean));
  assert.deepEqual(s.metrics.member_postcheck_valid, [true]);
  assert.equal(
    new Set(
      s.calls
        .filter((c) => c.params.tags.phase === "target")
        .map((c) => c.params.jar.cookies.access_token),
    ).size,
    10,
  );
  const invalid = load("delete");
  invalid.state.deletionMaxAge = 0;
  assert.throws(() => invalid.setup(), /DELETE warmup/);
});
test("missing/wrong/expired/duplicate fixtures and dynamic VU growth stop before measurement", () => {
  for (const mutate of [
    (f) => {
      f.baseUrl = "https://other.invalid";
    },
    (f) => {
      f.accounts[1].status = "WITHDRAWN";
    },
    (f) => {
      f.accounts[1].accessToken = f.accounts[0].accessToken;
    },
    (f) => {
      f.accounts[1].memberId = f.accounts[0].memberId;
    },
  ]) {
    const f = fixture("get");
    mutate(f);
    assert.throws(() =>
      load("get", { BASE_URL: "https://loadtest.invalid" }, f),
    );
  }
  assert.throws(() => load("delete", { RPS: "100", DURATION: "5m" }), /30000/);
  assert.throws(() => load("get", { VUS: "50", MAX_VUS: "100" }));
});
test("HTTP failures remain measurement failures without retry or account recycling", () => {
  const s = load("delete");
  const data = s.setup();
  s.state.failure = 500;
  s.run(data);
  s.teardown();
  assert.deepEqual(s.metrics.member_target_http_errors, [true]);
  assert.deepEqual(s.metrics.member_target_valid, [false]);
  assert.deepEqual(s.metrics.member_postcheck_valid, [false]);
  assert.equal(
    s.calls.filter((c) => c.params.tags.phase === "target").length,
    1,
  );
});
test("summary requires all target requests, latency, validation, postcheck and zero dropped", () => {
  const s = load();
  const metrics = {
    member_target_requests: { values: { count: 10 } },
    member_target_http_errors: { values: { rate: 0 } },
    member_target_valid: { values: { rate: 1 } },
    member_postcheck_valid: { values: { rate: 1 } },
    member_target_duration_ms: { values: { "p(95)": 4, "p(99)": 9 } },
  };
  assert.match(s.handleSummary({ metrics }).stdout, /판정: PASS/);
  metrics.dropped_iterations = { values: { count: 1 } };
  assert.match(s.handleSummary({ metrics }).stdout, /판정: FAIL/);
  assert.match(s.handleSummary({ metrics: {} }).stdout, /판정: FAIL/);
});
test("unchanged workflow forwards the private runner file path to this scenario", () => {
  const workflow = readFileSync(
    new URL("../.github/workflows/k6-dev.yml", import.meta.url),
    "utf8",
  );
  const source = workflow
    .split("python3 - <<'PY'\n")[1]
    .split("\n          PY")[0]
    .split("\n")
    .map((line) => line.slice(10))
    .join("\n");
  const harness = `import json,os,subprocess\nfrom unittest.mock import patch\nsource=json.loads(input())\ncalls=[]\ndef run(args):\n calls.append(args)\n return subprocess.CompletedProcess(args,0)\nwith patch.dict(os.environ,{'K6_PARAMS_JSON':json.dumps({'API':'get','RPS':100,'VUS':50,'MEMBER_ACCOUNTS_FILE':'/private/accounts.json'}),'BASE_URL':'https://dev.daynomy.com','LOADTEST_ID':'746-1','PORTFOLIO_TEST':'false','K6_SCRIPT_NAME':'member-v1-load.js'}),patch('os.path.isfile',return_value=False),patch('subprocess.run',run):\n try:exec(source)\n except SystemExit as e:assert e.code==0\nassert len(calls)==1\nassert 'MEMBER_ACCOUNTS_FILE=/private/accounts.json' in calls[0]\nassert calls[0][-1]=='member-v1-load.js'\nprint('PASS')`;
  const result = spawnSync("python3", ["-c", harness], {
    input: JSON.stringify(source),
    encoding: "utf8",
  });
  assert.equal(result.status, 0, result.stderr);
  assert.match(result.stdout, /PASS/);
});

test("offline preparation creates matching signed fixtures, rejects stale CSV and refuses overwrite", () => {
  const harness = `
import base64,csv,hashlib,hmac,importlib.util,json,os,tempfile
from pathlib import Path
from types import SimpleNamespace
import sys
spec=importlib.util.spec_from_file_location('prep',sys.argv[1])
p=importlib.util.module_from_spec(spec);spec.loader.exec_module(p)
assert p.count_for('get',500,'5m')==(1000,300)
assert p.count_for('delete',300,'5m')==(90000,300)
sql=p.seed_sql('lt746-check',1000)
assert "current_database() <> 'daynomy_dev'" in sql
assert 'BEGIN;' in sql and 'COMMIT;' in sql and 'generate_series(0,1000)' in sql
with tempfile.TemporaryDirectory() as directory:
 root=Path(directory);key=b'0123456789abcdef0123456789abcdef'
 env=root/'dev.env';env.write_text('JWT_SECRET='+base64.b64encode(key).decode()+'\\nJWT_ISSUER=daynomy-test\\n')
 data=root/'members.csv'
 fields=['test_number','member_id','provider_id','email','google_name','nickname','role','status']
 rows=[dict(test_number=i,member_id=i+1,provider_id=f'lt746-check-{i:07d}',email=f'lt746-check-{i:07d}@example.invalid',google_name='',nickname=f'seed{i}',role='USER',status='ACTIVE') for i in range(1001)]
 def write_csv():
  with data.open('w',newline='') as f:
   writer=csv.DictWriter(f,fieldnames=fields);writer.writeheader();writer.writerows(rows)
 write_csv()
 args=SimpleNamespace(env_file=env,issuer=None,token_seconds=3600,csv=data,namespace='lt746-check',api='get',base_url='https://dev.daynomy.com')
 fixture=p.token_fixture(args,1000,300)
 assert len(fixture['accounts'])==1001 and fixture['accounts'][0]['warmup']
 for row in fixture['accounts'][:3]:
  header,payload,signature=row['accessToken'].split('.')
  assert signature==p.b64(hmac.new(key,(header+'.'+payload).encode(),hashlib.sha256).digest())
  claims=json.loads(base64.urlsafe_b64decode(payload+'='*(-len(payload)%4)))
  assert claims['sub']==str(row['memberId']) and claims['token_type']=='access'
 output=root/'accounts.json';p.private_write(output,json.dumps(fixture))
 assert (output.stat().st_mode & 0o777)==0o600
 try:p.private_write(output,'overwrite')
 except FileExistsError:pass
 else:raise AssertionError('overwrote credentials')
 rows[1]['status']='WITHDRAWN';write_csv()
 try:p.token_fixture(args,1000,300)
 except ValueError:pass
 else:raise AssertionError('accepted a withdrawn member')
print('PASS')
`;
  const script = new URL("./prepare-member-v1.py", import.meta.url).pathname;
  const result = spawnSync(
    "python3",
    ["-c", harness, decodeURIComponent(script)],
    { encoding: "utf8" },
  );
  assert.equal(result.status, 0, result.stderr);
  assert.match(result.stdout, /PASS/);
});
