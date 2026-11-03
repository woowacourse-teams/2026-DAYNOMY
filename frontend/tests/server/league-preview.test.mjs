import assert from 'node:assert/strict';
import test from 'node:test';
import { createLeaguePreview } from '../../scripts/league-preview-data.mjs';

const get = (preview, path) => preview.request('GET', new URL(path, 'http://localhost')).data;
const scenario = (preview, value) =>
  preview.request('POST', new URL('/api/__league-preview/scenario', 'http://localhost'), {
    scenario: value,
  });

test('통합 순위에는 비공개 종목도 포함하고 공개 상세에는 공개 종목만 보여준다', () => {
  const preview = createLeaguePreview();
  const holdings = get(preview, '/api/users/me/shared-portfolio');
  assert.equal(holdings.holdings.length, 4);
  assert.equal(holdings.totalReturnRate, 21);
  const rank = get(preview, '/api/league/rankings').rankings.find(
    (row) => row.publicId === 'preview-me',
  );
  assert.equal(rank.weeklyReturnRate, 21);
  assert.equal(get(preview, '/api/league/investors/preview-me/details').holdings.length, 2);
});
test('원본 변경 예시는 공개 범위를 유지하며 새 종목을 비공개로 가져온다', () => {
  const preview = createLeaguePreview();
  scenario(preview, 'sync');
  const holdings = get(preview, '/api/users/me/shared-portfolio').holdings;
  assert.equal(holdings.find((h) => h.assetId === 1).quantity, 15);
  assert.equal(holdings.find((h) => h.assetId === 1).hidden, false);
  assert.equal(holdings.find((h) => h.assetId === 3).hidden, true);
  assert.equal(holdings.find((h) => h.assetId === 5).hidden, true);
  assert.equal(
    holdings.some((h) => h.assetId === 4),
    false,
  );
  assert.equal(
    get(preview, '/api/league/rankings').rankings.find((row) => row.publicId === 'preview-me')
      .weeklyReturnRate,
    21,
  );
});
test('중도 참가자도 부분 성과를 표시하고 순위에 즉시 포함한다', () => {
  const preview = createLeaguePreview();
  scenario(preview, 'sync');
  scenario(preview, 'partial');
  assert.equal(get(preview, '/api/users/me/shared-portfolio').totalReturnRate, 21);
  assert.equal(get(preview, '/api/league/investors/preview-me/daily-history').weeklyReturnRate, 10);
  assert.equal(
    get(preview, '/api/league/rankings').rankings.some((row) => row.publicId === 'preview-me'),
    true,
  );
  assert.equal(
    preview.request('POST', new URL('/api/not-in-preview', 'http://localhost'), {}).status,
    404,
  );
});

test('원본 화면의 수정도 공개 상태를 유지하며 리그 목록에 반영한다', () => {
  const preview = createLeaguePreview();
  const path = new URL('/api/users/me/portfolio/holdings/3', 'http://localhost');
  assert.equal(
    preview.request('PATCH', path, { quantity: 8, averagePurchasePrice: 100000 }).status,
    200,
  );
  const h = get(preview, '/api/users/me/shared-portfolio').holdings.find(
    (value) => value.assetId === 3,
  );
  assert.equal(h.quantity, 8);
  assert.equal(h.hidden, true);
  assert.equal(
    preview.request('PATCH', path, { quantity: -1, averagePurchasePrice: 100000 }).status,
    400,
  );
});

test('월~일 7일의 휴장일 누적은 유지하고 미국 수집 지연은 대기와 토요일 예고로 표시한다', () => {
  const preview = createLeaguePreview();
  scenario(preview, 'holiday');
  const holiday = get(preview, '/api/league/investors/preview-me/daily-history');
  assert.equal(holiday.days.length, 7);
  assert.equal(holiday.days[6].baseDate, '2026-11-01');
  for (const day of holiday.days.slice(5)) {
    assert.equal(day.dailyReturnRate, 0);
    assert.equal(day.cumulativeReturnRate, 10);
    assert.equal(day.reason, 'MARKET_CLOSED');
  }
  scenario(preview, 'us-wait');
  const waiting = get(preview, '/api/league/investors/preview-me/daily-history');
  assert.equal(waiting.days[4].cumulativeReturnRate, null);
  assert.equal(waiting.aggregation.expectedAt, '2026-10-31T08:20:00Z');
  assert.equal(get(preview, '/api/league/rankings').rankings[1].weeklyReturnRate, 10);
});

test('수요일 참가도 이미 저장된 월·화 스냅샷 성과를 가져오고 비공개 종목을 유지한다', () => {
  const preview = createLeaguePreview();
  assert.equal(scenario(preview, 'snapshot').status, 200);
  const history = get(preview, '/api/league/investors/preview-me/daily-history');
  assert.equal(history.days[0].cumulativeReturnRate, 10);
  assert.equal(history.days[1].cumulativeReturnRate, 21);
  assert.equal(history.weeklyReturnRate, 21);
  assert.equal(
    get(preview, '/api/users/me/shared-portfolio').holdings.filter((h) => h.hidden).length,
    2,
  );
  assert.equal(
    get(preview, '/api/league/rankings').rankings.some((row) => row.publicId === 'preview-me'),
    true,
  );
});

test('개인 성과는 먼저 갱신하고 통합 순위는 같은 기준일을 유지한 뒤 함께 갱신한다', () => {
  const preview = createLeaguePreview();
  scenario(preview, 'common-wait');
  const monday = get(preview, '/api/league/rankings');
  assert.equal(monday.asOfDate, '2026-10-26');
  assert.equal(monday.rankings.find((row) => row.publicId === 'preview-me').weeklyReturnRate, 10);
  const personal = get(preview, '/api/league/investors/preview-me');
  assert.equal(personal.performance.weeklyReturnRate, 21);
  assert.equal(personal.performance.asOfDate, '2026-10-27');
  assert.equal(personal.performance.rankingAsOfDate, '2026-10-26');
  scenario(preview, 'common-ready');
  const tuesday = get(preview, '/api/league/rankings');
  assert.equal(tuesday.asOfDate, '2026-10-27');
  assert.equal(tuesday.rankings[0].publicId, 'preview-me');
  assert.equal(tuesday.rankings[0].weeklyReturnRate, 21);
  scenario(preview, 'member-gap');
  const gap = get(preview, '/api/league/rankings');
  assert.equal(gap.asOfDate, '2026-10-27');
  assert.equal(
    gap.rankings.some((row) => row.publicId === 'preview-me'),
    false,
  );
  const last = get(preview, '/api/league/investors/preview-me/daily-history');
  assert.equal(last.weeklyReturnRate, 10);
  assert.equal(last.asOfDate, '2026-10-26');
  assert.equal(last.days[1].cumulativeReturnRate, null);
});
