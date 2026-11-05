import { useEffect, useRef, useState } from 'react';
import { useLocation } from 'react-router-dom';
import { useAuth } from '../../hooks/useLoginStatus';
import { isStaffHostname } from '../../serviceDomain';
import { getCurrentPmfSurvey, hidePmfSurveyToday, submitPmfResponse } from './api';
import { PmfSurveyDialog } from './PmfSurveyDialog';
import {
  nextKoreanMidnight,
  readSurveyPreference,
  surveyStorageKey,
  writeSurveyPreference,
} from './storage';
import type { PmfSurveyStatus } from './types';

export function PmfSurveyPrompt() {
  const { pathname } = useLocation();
  const { loading, memberId, role } = useAuth();
  const allowed = !loading && role !== 'ADMIN' && !isStaffHostname(window.location.hostname);
  if (!allowed || pathname !== '/') return null;
  return <CurrentPmfSurvey key={memberId ?? 'guest'} memberId={memberId} />;
}

function CurrentPmfSurvey({ memberId }: { memberId: number | null | undefined }) {
  const [survey, setSurvey] = useState<PmfSurveyStatus | null>(null);
  const [visible, setVisible] = useState(false);
  const [dismissed, setDismissed] = useState(false);
  const submission = useRef<AbortController | null>(null);
  useEffect(() => {
    const controller = new AbortController();
    void getCurrentPmfSurvey(controller.signal)
      .then((current) => {
        if (!controller.signal.aborted && current?.eligible && current.responseId === null)
          setSurvey(current);
      })
      .catch(() => {
        /* Unknown eligibility must not interrupt the portfolio. */
      });
    return () => {
      controller.abort();
      submission.current?.abort();
    };
  }, [memberId]);

  useEffect(() => {
    if (!survey || dismissed) return;
    const key = surveyStorageKey(survey.surveyId, memberId);
    function suppressed() {
      const preference = readSurveyPreference(key);
      return (
        preference.answered ||
        preference.hiddenUntil > Date.now() ||
        (survey?.hiddenUntil ? Date.parse(survey.hiddenUntil) > Date.now() : false)
      );
    }
    function openWhenReady() {
      if (
        !suppressed() &&
        document.visibilityState !== 'hidden' &&
        !document.querySelector('dialog[open]')
      )
        setVisible(true);
    }
    function sync(event: StorageEvent) {
      if ((event.key === key || event.key === null) && suppressed()) setDismissed(true);
    }
    // A snooze that expires while this dashboard is open waits for a new entry.
    if (suppressed()) return;
    openWhenReady();
    document.addEventListener('close', openWhenReady, true);
    document.addEventListener('visibilitychange', openWhenReady);
    window.addEventListener('storage', sync);
    return () => {
      document.removeEventListener('close', openWhenReady, true);
      document.removeEventListener('visibilitychange', openWhenReady);
      window.removeEventListener('storage', sync);
    };
  }, [survey, dismissed, memberId]);

  if (!survey || !visible || dismissed) return null;
  const key = surveyStorageKey(survey.surveyId, memberId);
  return (
    <PmfSurveyDialog
      onSubmit={async (answer, reason) => {
        const controller = new AbortController();
        submission.current = controller;
        try {
          await submitPmfResponse(survey.surveyId, answer, reason, controller.signal);
          controller.signal.throwIfAborted();
          writeSurveyPreference(key, { answered: true });
        } finally {
          submission.current = null;
        }
      }}
      onHideToday={async () => {
        const localSaved = writeSurveyPreference(key, { hiddenUntil: nextKoreanMidnight() });
        const controller = new AbortController();
        submission.current = controller;
        try {
          await hidePmfSurveyToday(survey.surveyId, controller.signal);
          controller.signal.throwIfAborted();
          setDismissed(true);
          return 'saved';
        } catch (error) {
          controller.signal.throwIfAborted();
          if (localSaved) return 'local-only';
          throw error;
        } finally {
          submission.current = null;
        }
      }}
      onDismiss={() => setDismissed(true)}
    />
  );
}
