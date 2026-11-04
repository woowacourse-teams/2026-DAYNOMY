export const PMF_QUESTION = '이 제품을 더 이상 쓸 수 없다면 어떻겠습니까?';

export const PMF_ANSWERS = [
  { value: 'VERY_DISAPPOINTED', label: '매우 실망' },
  { value: 'SOMEWHAT_DISAPPOINTED', label: '약간 실망' },
  { value: 'NOT_DISAPPOINTED', label: '실망하지 않음' },
  { value: 'NO_LONGER_USING', label: '더 이상 쓰지 않음' },
] as const;

export type PmfAnswer = (typeof PMF_ANSWERS)[number]['value'];

export const PMF_REASON_MAX_LENGTH = 500;

export type PmfSurveyStatus = {
  surveyId: number;
  eligible: boolean;
  responseId: number | null;
};
