export const GUIDE_PROGRESS_STORAGE_KEY = 'daynomy:guide-progress:v1';

export type GuideProgress = Record<string, string[]>;

export function loadGuideProgress(): GuideProgress {
  try {
    const saved = localStorage.getItem(GUIDE_PROGRESS_STORAGE_KEY);
    if (!saved) return {};

    const parsed: unknown = JSON.parse(saved);
    if (typeof parsed !== 'object' || parsed === null || Array.isArray(parsed)) return {};

    return Object.fromEntries(
      Object.entries(parsed).filter(
        (entry): entry is [string, string[]] =>
          Array.isArray(entry[1]) && entry[1].every((value) => typeof value === 'string'),
      ),
    );
  } catch {
    return {};
  }
}

export function saveGuideSteps(guideId: string, completedSteps: string[]) {
  try {
    const progress = loadGuideProgress();
    localStorage.setItem(
      GUIDE_PROGRESS_STORAGE_KEY,
      JSON.stringify({ ...progress, [guideId]: [...new Set(completedSteps)] }),
    );
  } catch {
    // 저장소를 사용할 수 없어도 현재 화면의 가이드는 계속 진행한다.
  }
}

export function completeGuide(guideId: string) {
  saveGuideSteps(guideId, ['completed']);
}

export function isGuideComplete(guideId: string, requiredStepCount?: number) {
  const completedSteps = loadGuideProgress()[guideId] ?? [];
  return requiredStepCount === undefined
    ? completedSteps.includes('completed')
    : completedSteps.length >= requiredStepCount;
}
