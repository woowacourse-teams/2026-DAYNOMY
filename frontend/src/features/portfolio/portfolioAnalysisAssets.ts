import type { PortfolioAsset, PortfolioHoldingResult } from './types';

export function toPortfolioAnalysisAssets(holdings: PortfolioHoldingResult[]): PortfolioAsset[] {
  const assets = holdings
    .filter(({ weight }) => weight > 0)
    .map(({ name, weight }) => ({ assetName: name, weight }));
  if (assets.length === 0) return [];

  const totalWeight = assets.reduce((sum, asset) => sum + asset.weight, 0);
  const adjustmentIndex = assets.reduce(
    (largestIndex, asset, index) =>
      asset.weight > assets[largestIndex].weight ? index : largestIndex,
    0,
  );

  return assets.map((asset, index) =>
    index === adjustmentIndex
      ? { ...asset, weight: Number((asset.weight + 100 - totalWeight).toFixed(2)) }
      : asset,
  );
}
