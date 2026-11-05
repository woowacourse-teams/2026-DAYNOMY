const DAY = 86_400_000;
const KST = 9 * 3_600_000;

export function nextKoreanMidnight(now = Date.now()) {
  return (Math.floor((now + KST) / DAY) + 1) * DAY - KST;
}

export function surveyStorageKey(surveyId: number, memberId: number | null | undefined) {
  return `daynomy:pmf-survey:v2:${surveyId}:${memberId ?? 'guest'}`;
}

export function readSurveyPreference(key: string): { answered: boolean; hiddenUntil: number } {
  try {
    const value: unknown = JSON.parse(localStorage.getItem(key) ?? 'null');
    if (typeof value === 'object' && value !== null) {
      return {
        answered: 'answered' in value && value.answered === true,
        hiddenUntil:
          'hiddenUntil' in value &&
          typeof value.hiddenUntil === 'number' &&
          Number.isFinite(value.hiddenUntil)
            ? value.hiddenUntil
            : 0,
      };
    }
  } catch {
    // Invalid or restricted browser storage must not break the dashboard.
  }
  return { answered: false, hiddenUntil: 0 };
}

export function writeSurveyPreference(
  key: string,
  value: { answered?: boolean; hiddenUntil?: number },
) {
  try {
    localStorage.setItem(key, JSON.stringify({ ...readSurveyPreference(key), ...value }));
    return true;
  } catch {
    return false;
  }
}
