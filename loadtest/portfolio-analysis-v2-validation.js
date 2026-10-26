// V2는 선택한 종목과 제외한 종목을 합쳐 원래 포트폴리오를 보존해야 한다.
export function validateSelection(analysis, assetNames) {
  const impacts = analysis?.impacts;
  const excluded = analysis?.excludedAssets;
  const selectedCount = Math.min(5, assetNames.length);
  const all = Array.isArray(impacts) && Array.isArray(excluded) ? [...impacts, ...excluded] : [];
  const assetsValid = Array.isArray(impacts) && Array.isArray(excluded) &&
    analysis.totalAssetCount === assetNames.length &&
    analysis.selectedAssetCount === selectedCount && impacts.length === selectedCount &&
    excluded.length === assetNames.length - selectedCount &&
    new Set(all.map((a) => a.assetName)).size === assetNames.length &&
    all.every((a) => assetNames.includes(a.assetName));
  const weightsValid = assetsValid &&
    all.every((a) => typeof a.weight === 'number' && Number.isFinite(a.weight) && a.weight > 0) &&
    Math.abs(all.reduce((sum, a) => sum + a.weight, 0) - 100) < 0.02 &&
    (excluded.length === 0 || (analysis.overallDirection === null && analysis.overallScore === null));
  const analyzedCount = assetsValid ? impacts.filter((i) => i.direction != null).length : 0;
  const expectedCoverage = analyzedCount === 0 ? 'INSUFFICIENT'
    : analyzedCount === assetNames.length ? 'COMPLETE' : 'PARTIAL';
  return { assetsValid, weightsValid, analyzedCount, expectedCoverage };
}
