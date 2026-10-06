// 이전 저장 키는 읽기 호환용으로만 유지한다. 새 기록은 기능명 기반 키에 저장한다.
const legacyKeys: Record<string, string> = {
  'daynomy:financial-plans:v1': 'daynomy:romi-consultations',
  'daynomy:account-steps:v1': 'daynomy:romi-account-steps',
  'daynomy:learning-progress:v1': 'daynomy:romi-progress:v1',
  'daynomy:weekly-check-ins:v1': 'daynomy:romi-check-ins:v1',
  'daynomy:simulated-trades:v1': 'daynomy:romi-mock-trades:v1',
};

export function readFinancialStorage(key: string, storage: Storage = localStorage): string | null {
  const current = storage.getItem(key);
  if (current !== null) return current;
  const legacyKey = legacyKeys[key];
  return legacyKey ? storage.getItem(legacyKey) : null;
}
