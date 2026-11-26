export const SIMULATION_CATEGORIES = {
  STOCK: '주식',
  ETF: 'ETF',
} as const;

export type SimulationCategory = keyof typeof SIMULATION_CATEGORIES;
export type SimulationAsset = {
  assetId: number;
  assetName: string;
  category: SimulationCategory;
  amount: number;
};
export type SimulationDraft = Omit<SimulationAsset, 'amount'> & { weight: string };

export function isSimulationCategory(value: unknown): value is SimulationCategory {
  return typeof value === 'string' && Object.hasOwn(SIMULATION_CATEGORIES, value);
}

export function analysisAssetName(asset: Pick<SimulationAsset, 'assetName' | 'category'>) {
  return asset.assetName.trim();
}

export function amountInCents(amount: string): bigint | null {
  if (!/^\d+(\.\d{1,2})?$/.test(amount) || amount.length > 16) return null;
  const [whole, fraction = ''] = amount.split('.');
  const cents = BigInt(whole) * 100n + BigInt(fraction.padEnd(2, '0'));
  return cents > 0n && cents <= 100_000_000_000_000n ? cents : null;
}

export function weightInBasisPoints(weight: string): number | null {
  const units = amountInCents(weight);
  return units !== null && units <= 10000n ? Number(units) : null;
}

export function simulationWeights(assets: SimulationDraft[]) {
  if (!assets.length || assets.length > 50) return null;
  const units = assets.map((asset) => weightInBasisPoints(asset.weight));
  if (units.some((unit) => unit === null)) return null;
  const validUnits = units as number[];
  return {
    total: validUnits.reduce((sum, unit) => sum + unit, 0) / 100,
    weights: validUnits.map((unit) => unit / 100),
  };
}

export function balanceSimulationDraft(assets: SimulationDraft[]) {
  if (!assets.length || assets.length > 50) return null;
  const manualUnits = assets.slice(1).map((asset) => weightInBasisPoints(asset.weight));
  if (manualUnits.some((unit) => unit === null)) return null;
  const remaining = 10000 - (manualUnits as number[]).reduce((sum, unit) => sum + unit, 0);
  if (remaining <= 0) return null;
  return assets.map((asset, index) =>
    index === 0 ? { ...asset, weight: (remaining / 100).toFixed(2) } : asset,
  );
}

export function simulationAllocation(assets: { amount: string }[]) {
  if (!assets.length || assets.length > 50) return null;
  const amounts = assets.map((asset) => amountInCents(asset.amount));
  if (amounts.some((amount) => amount === null)) return null;
  const cents = amounts as bigint[];
  const total = cents.reduce((sum, amount) => sum + amount, 0n);
  const units = cents.map((amount) => Number((amount * 10000n) / total));
  const remainders = cents.map((amount, index) => ({
    index,
    remainder: (amount * 10000n) % total,
  }));
  remainders.sort((a, b) =>
    a.remainder === b.remainder ? a.index - b.index : a.remainder > b.remainder ? -1 : 1,
  );
  // 소수점 두 자리 반올림 잔여분을 큰 나머지 순서로 배분해 합계를 정확히 100%로 맞춘다.
  const remaining = 10000 - units.reduce((sum, unit) => sum + unit, 0);
  for (let i = 0; i < remaining; i++) units[remainders[i].index]++;
  return { total: Number(total) / 100, weights: units.map((unit) => unit / 100) };
}
