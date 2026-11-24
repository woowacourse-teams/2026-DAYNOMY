import { test, expect } from '@playwright/test';
import { execFileSync } from 'node:child_process';
import { createHmac, randomUUID } from 'node:crypto';
import { mkdirSync, writeFileSync } from 'node:fs';

// Runs against an isolated CI database and real app. No production/test-account credentials.
function sql(statement: string) {
  if (process.env.PGDATABASE !== 'daynomy_idempotency_ci' || process.env.PGHOST !== '127.0.0.1')
    throw new Error('Only the isolated localhost CI database is allowed');
  return execFileSync('psql', ['-X', '-q', '-A', '-t', '-v', 'ON_ERROR_STOP=1', '-c', statement], {
    encoding: 'utf8',
    stdio: ['ignore', 'pipe', 'pipe'],
  }).trim();
}

test('화면 응답 유실·새로고침·새 매수와 실제 DB 정합성 및 요청별 SQL 비용', async ({
  page,
  context,
}) => {
  const today = new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Seoul' }).format(new Date());
  const member = Number(
    sql(
      "INSERT INTO members(provider,provider_id,email,nickname,role,status,created_at,updated_at) VALUES('GOOGLE','idem-ci','idem-ci@example.invalid','idem-ci','USER','ACTIVE',now(),now()) RETURNING id",
    ),
  );
  const asset = Number(
    sql(
      "INSERT INTO assets(asset_code,name,category,market,currency,listed,created_at,updated_at) VALUES('IDEMCI','멱등성실험종목','STOCK','KOSPI','KRW',true,now(),now()) RETURNING id",
    ),
  );
  sql(
    `INSERT INTO stock_daily_prices(asset_id,base_date,close_price,created_at,updated_at) VALUES(${asset},'${today}',100,now(),now())`,
  );
  const encode = (value: unknown) => Buffer.from(JSON.stringify(value)).toString('base64url');
  const now = Math.floor(Date.now() / 1000);
  const data = `${encode({ alg: 'HS256', typ: 'JWT' })}.${encode({ iss: 'daynomy', sub: String(member), iat: now, exp: now + 900, jti: randomUUID(), sid: randomUUID(), token_type: 'access', role: 'USER' })}`;
  const token = `${data}.${createHmac('sha256', Buffer.from(process.env.JWT_SECRET!, 'base64')).update(data).digest('base64url')}`;
  await context.addCookies([
    {
      name: 'access_token',
      value: token,
      domain: '127.0.0.1',
      path: '/',
      httpOnly: true,
      sameSite: 'Lax',
    },
  ]);
  await page.addInitScript(() => {
    localStorage.setItem('daynomy:portfolio-tour:v1', 'done');
    localStorage.setItem('daynomy:service-started:v1', '1');
  });
  const keys: string[] = [];
  let drop = true;
  await page.route('**/api/portfolio/transactions', async (route) => {
    if (route.request().method() !== 'POST') return route.continue();
    keys.push(route.request().headers()['idempotency-key']!);
    const response = await route.fetch();
    expect(response.status()).toBe(201);
    if (drop) {
      drop = false;
      await route.abort('connectionreset');
    } else await route.fulfill({ response });
  });
  function counts() {
    return JSON.parse(
      sql(
        `SELECT json_build_object('histories',(SELECT count(*) FROM portfolio_holding_histories h JOIN portfolios p ON p.id=h.portfolio_id WHERE p.member_id=${member}),'quantity',(SELECT coalesce(sum(h.quantity),0) FROM portfolio_holdings h JOIN portfolios p ON p.id=h.portfolio_id WHERE p.member_id=${member}),'requests',(SELECT count(*) FROM portfolio_transaction_requests WHERE member_id=${member}))`,
      ),
    );
  }
  async function openTrade() {
    await page.getByRole('button', { name: '종목 추가', exact: true }).first().click();
    await page.getByRole('button', { name: '한 종목 추가' }).click();
    const dialog = page.getByRole('dialog', { name: '거래 기록' });
    await dialog.getByRole('searchbox').fill('멱등성실험종목');
    await dialog.getByRole('button', { name: /멱등성실험종목/ }).click();
    await dialog.getByLabel('수량 (주)').fill('1');
    await dialog.getByLabel('거래 단가 (원)').fill('100');
    return dialog;
  }
  await page.goto('/');
  // The route may show the empty-portfolio introduction before the first trade.
  const start = page.getByRole('button', { name: '포트폴리오 만들기', exact: true });
  await start
    .or(page.getByRole('button', { name: '종목 추가', exact: true }).first())
    .first()
    .waitFor();
  let dialog;
  if (await start.isVisible()) {
    await start.click();
    dialog = page.getByRole('dialog', { name: '거래 기록' });
    await dialog.getByRole('searchbox').fill('멱등성실험종목');
    await dialog.getByRole('button', { name: /멱등성실험종목/ }).click();
    await dialog.getByLabel('수량 (주)').fill('1');
    await dialog.getByLabel('거래 단가 (원)').fill('100');
  } else dialog = await openTrade();
  await dialog.getByRole('button', { name: /추가하기|기록하기/ }).click();
  await expect(dialog.getByRole('alert')).toBeVisible();
  expect(counts()).toEqual({ histories: 1, quantity: 1, requests: 1 });
  await page.reload();
  await page.getByRole('button', { name: '이전 거래 결과 확인' }).click();
  await expect(page.getByRole('button', { name: '이전 거래 결과 확인' })).toHaveCount(0);
  expect(keys).toHaveLength(2);
  expect(keys[1]).toBe(keys[0]);
  expect(counts()).toEqual({ histories: 1, quantity: 1, requests: 1 });
  const next = await openTrade();
  await next.getByRole('button', { name: /추가하기|기록하기/ }).click();
  await expect(page.getByRole('dialog', { name: '거래 기록' })).toHaveCount(0);
  expect(keys).toHaveLength(3);
  expect(keys[2]).not.toBe(keys[0]);
  expect(counts()).toEqual({ histories: 2, quantity: 2, requests: 2 });
  const functional = {
    afterResponseLossAndRetry: { histories: 1, quantity: 1, requests: 1 },
    afterNewIntent: counts(),
    sameKeyReused: keys[0] === keys[1],
    newKeyCreated: keys[0] !== keys[2],
  };
  await page.close(); // Prevent UI refresh queries from contaminating SQL measurement.

  const csrfResponse = await context.request.get('/api/auth/csrf');
  expect(csrfResponse.ok()).toBeTruthy();
  const csrf = await csrfResponse.json();
  const body = {
    assetId: asset,
    type: 'BUY',
    tradeDate: today,
    quantity: 1,
    unitPrice: 100,
    fee: 0,
    tax: 0,
  };
  const post = (key?: string) =>
    context.request.post('/api/portfolio/transactions', {
      data: body,
      headers: { [csrf.headerName]: csrf.token, ...(key ? { 'Idempotency-Key': key } : {}) },
    });
  for (let i = 0; i < 3; i++) expect((await post(randomUUID())).status()).toBe(201);
  const completedKey = randomUUID();
  expect((await post(completedKey)).status()).toBe(201);
  const measurements = [];
  for (const mode of ['without-key', 'new-key', 'replay'] as const) {
    sql('SELECT pg_stat_statements_reset()');
    const times: number[] = [];
    for (let i = 0; i < 20; i++) {
      const before = performance.now();
      const result = await post(
        mode === 'without-key' ? undefined : mode === 'new-key' ? randomUUID() : completedKey,
      );
      times.push(performance.now() - before);
      expect(result.status()).toBe(201);
    }
    const statements = JSON.parse(
      sql(
        "SELECT coalesce(json_agg(t),'[]') FROM (SELECT query,calls FROM pg_stat_statements WHERE dbid=(SELECT oid FROM pg_database WHERE datname=current_database()) AND query NOT LIKE '%pg_stat_statements%' ORDER BY calls DESC) t",
      ),
    );
    times.sort((a, b) => a - b);
    measurements.push({
      mode,
      samples: times.length,
      medianMs: times[10],
      p95Ms: times[18],
      sqlCallsPerRequest:
        statements.reduce((sum: number, row: { calls: number }) => sum + Number(row.calls), 0) / 20,
      statements,
    });
  }
  const table = JSON.parse(
    sql(
      "SELECT json_build_object('rows',count(*),'rowBytes',sum(pg_column_size(r)),'tableAndIndexBytes',pg_total_relation_size('portfolio_transaction_requests')) FROM portfolio_transaction_requests r",
    ),
  );
  mkdirSync('integration-results', { recursive: true });
  writeFileSync(
    'integration-results/actual-api-cost.json',
    JSON.stringify(
      {
        environment: 'isolated GitHub CI / real Spring API and PostgreSQL / localhost',
        concurrentUsers: 1,
        functional,
        measurements,
        table,
        limitation:
          'Not development-server latency or production traffic. SQL calls include transaction utility statements and auth. No DB/Redis restarts tested here.',
      },
      null,
      2,
    ),
  );
});
