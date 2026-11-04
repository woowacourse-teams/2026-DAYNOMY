import { useEffect, useRef, useState } from 'react';
import { useLocation } from 'react-router-dom';
import { useAuth } from '../../hooks/useLoginStatus';
import { isStaffHostname } from '../../serviceDomain';
import { getCurrentPmfSurvey, submitPmfAnswer, submitPmfReason } from './api';
import { PmfSurveyDialog } from './PmfSurveyDialog';
import type { PmfSurveyStatus } from './types';

function dismissalKey(surveyId: number, memberId: number | null | undefined) {
  return `daynomy:pmf-survey:v1:${surveyId}:${memberId ?? 'guest'}:dismissed`;
}

function wasDismissed(key: string) {
  try {
    return sessionStorage.getItem(key) === 'true';
  } catch {
    return false;
  }
}

function rememberDismissal(key: string) {
  try {
    sessionStorage.setItem(key, 'true');
  } catch {
    // Storage restrictions must not prevent closing the survey.
  }
}

export function PmfSurveyPrompt() {
  const { pathname } = useLocation();
  const { loading, memberId, role } = useAuth();
  const allowed = !loading && role !== 'ADMIN' && !isStaffHostname(window.location.hostname);

  // Unmount on navigation/account changes so one user's pending form cannot reach another account.
  if (!allowed || pathname !== '/') return null;
  return <CurrentPmfSurvey key={memberId ?? 'guest'} memberId={memberId} />;
}

function CurrentPmfSurvey({ memberId }: { memberId: number | null | undefined }) {
  const [survey, setSurvey] = useState<PmfSurveyStatus | null>(null);
  const [visible, setVisible] = useState(false);
  const [dismissed, setDismissed] = useState(false);
  const responseId = useRef<number | null>(null);
  const submission = useRef<AbortController | null>(null);

  useEffect(() => {
    const controller = new AbortController();
    void getCurrentPmfSurvey(controller.signal)
      .then((current) => {
        if (
          !controller.signal.aborted &&
          current?.eligible &&
          current.responseId === null &&
          !wasDismissed(dismissalKey(current.surveyId, memberId))
        ) {
          setSurvey(current);
        }
      })
      .catch(() => {
        // Eligibility is optional to the portfolio. Unknown/error states must never open a survey.
      });
    return () => {
      controller.abort();
      submission.current?.abort();
    };
  }, [memberId]);

  useEffect(() => {
    if (!survey || dismissed) return;
    function openWhenReady() {
      if (document.visibilityState !== 'hidden' && !document.querySelector('dialog[open]')) {
        setVisible(true);
      }
    }
    openWhenReady();
    document.addEventListener('close', openWhenReady, true);
    document.addEventListener('visibilitychange', openWhenReady);
    return () => {
      document.removeEventListener('close', openWhenReady, true);
      document.removeEventListener('visibilitychange', openWhenReady);
    };
  }, [survey, dismissed]);

  if (!survey || !visible || dismissed) return null;

  return (
    <PmfSurveyDialog
      onSubmitAnswer={async (answer) => {
        const controller = new AbortController();
        submission.current = controller;
        try {
          const savedId = await submitPmfAnswer(survey.surveyId, answer, controller.signal);
          controller.signal.throwIfAborted();
          responseId.current = savedId;
          // The first answer is durable; reopening/reloading must not request another answer.
          rememberDismissal(dismissalKey(survey.surveyId, memberId));
        } finally {
          submission.current = null;
        }
      }}
      onSubmitReason={async (reason) => {
        if (responseId.current === null) throw new Error('저장된 설문 응답이 없습니다.');
        const controller = new AbortController();
        submission.current = controller;
        try {
          await submitPmfReason(survey.surveyId, responseId.current, reason, controller.signal);
          controller.signal.throwIfAborted();
        } finally {
          submission.current = null;
        }
      }}
      onDismiss={() => {
        rememberDismissal(dismissalKey(survey.surveyId, memberId));
        setDismissed(true);
      }}
    />
  );
}
