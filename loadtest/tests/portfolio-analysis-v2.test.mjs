import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import vm from 'node:vm';

const source = readFileSync(new URL('../portfolio-analysis-v2-validation.js', import.meta.url), 'utf8');
const { validateSelection } = await import(`data:text/javascript;base64,${Buffer.from(source).toString('base64')}`);
const names = Array.from({ length: 50 }, (_, i) => `stock${i}`);
const analysis = () => ({
  totalAssetCount: 50, selectedAssetCount: 5, overallDirection: null, overallScore: null,
  impacts: names.slice(0, 5).map(assetName => ({ assetName, weight: 2, direction: 'POSITIVE' })),
  excludedAssets: names.slice(5).map(assetName => ({ assetName, weight: 2 })),
});

test('accepts selected 5 and excluded 45 with original weights and partial coverage', () => {
  assert.deepEqual(validateSelection(analysis(), names), {
    assetsValid: true, weightsValid: true, analyzedCount: 5, expectedCoverage: 'PARTIAL',
  });
});

test('rejects renormalized weights, duplicate holdings, full analysis, and overall claims', () => {
  const weights = analysis(); weights.impacts.forEach(i => i.weight = 20);
  assert.equal(validateSelection(weights, names).weightsValid, false);
  const duplicate = analysis(); duplicate.excludedAssets[0].assetName = names[0];
  assert.equal(validateSelection(duplicate, names).assetsValid, false);
  const full = analysis(); full.impacts = [...full.impacts, ...full.excludedAssets]; full.excludedAssets = [];
  assert.equal(validateSelection(full, names).assetsValid, false);
  const score = analysis(); score.overallScore = 10;
  assert.equal(validateSelection(score, names).weightsValid, false);
  assert.equal(validateSelection(null, names).assetsValid, false);
});

test('distinguishes insufficient evidence from excluded holdings and accepts small portfolios', () => {
  const value = analysis(); value.impacts.forEach(i => i.direction = null);
  assert.equal(validateSelection(value, names).expectedCoverage, 'INSUFFICIENT');
  const small = { totalAssetCount: 1, selectedAssetCount: 1, impacts: [{ assetName: 'A', weight: 100, direction: 'POSITIVE' }], excludedAssets: [] };
  assert.equal(validateSelection(small, ['A']).expectedCoverage, 'COMPLETE');
  assert.equal(validateSelection(small, ['A']).weightsValid, true);
});

for (const file of ['portfolio-analysis-v2.js', 'portfolio-analysis-v2-rps.js']) {
  test(`${file} uses subset validation for measurement`, async () => {
    const requests = [];
    class Metric { add() {} }
    const account = { memberId: 1, accessToken: 'a.b.c', assetNames: names };
    const warmup = { memberId: 2, accessToken: 'd.e.f', warmup: true, assetNames: names.map(n => `warmup-${n}`) };
    const a = analysis(); a.analyzedAssetCount = 5; a.coverage = 'PARTIAL'; a.overallImpact = '분석했어요.'; a.analyzedWeight = 10;
    let checked = false;
    const context = vm.createContext({ __ENV: { BASE_URL: 'https://example.com', RPS: '1', DURATION: '1s', USERS: '1' },
      open: () => JSON.stringify([account, warmup]), __VU: 1, console, Date, Set,
    });
    const modules = {
      'k6/http': { default: { post: () => { requests.push(1); return { status: 200, json: () => ({ status: 'READY', analysisId: 1, matchesCurrentPortfolio: true, analysis: a }) }; } } },
      k6: { check: (response, checks) => { checked = true; assert.ok(Object.values(checks).every(c => c(response))); return true; }, sleep() {} },
      'k6/execution': { default: { scenario: { iterationInTest: 0 } } },
      'k6/metrics': { Counter: Metric, Rate: Metric, Trend: Metric },
    };
    const entry = new vm.SourceTextModule(readFileSync(new URL(`../${file}`, import.meta.url), 'utf8'), { context });
    await entry.link(async name => {
      if (name.endsWith('validation.js')) {
        const helper = new vm.SourceTextModule(source, { context }); await helper.link(() => {}); return helper;
      }
      const exports = modules[name];
      return new vm.SyntheticModule(Object.keys(exports), function () { for (const [key, value] of Object.entries(exports)) this.setExport(key, value); }, { context });
    });
    await entry.evaluate();
    entry.namespace.default({ csrf: [{ cookie: 'x', headerName: 'X-CSRF', token: 't' }], startAt: 0 });
    assert.equal(checked, true); assert.equal(requests.length, 1);
  });
}
