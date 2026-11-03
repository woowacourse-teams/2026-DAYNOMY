import { useEffect, useRef, useState } from 'react';
import { claimReviewReminder, snoozeReviewReminder } from './api';
import { leagueErrorType, trackLeagueEvent, type ReviewReminderEntryPoint } from './analytics';
import { FloatingReviewCoach } from './FloatingReviewCoach';
import type { InvestmentDecisionRecord } from './types';

export function LeagueReviewReminder({
  records: providedRecords,
  entryPoint = 'ranking',
  onReviewed,
}: {
  records?: InvestmentDecisionRecord[] | null;
  entryPoint?: ReviewReminderEntryPoint;
  onReviewed?: (record: InvestmentDecisionRecord) => void;
}) {
  const [requestKey] = useState(() => crypto.randomUUID());
  const [records, setRecords] = useState<InvestmentDecisionRecord[]>([]);
  const [remind, setRemind] = useState(true);
  const [completedIds, setCompletedIds] = useState<number[]>([]);
  const [editingRecordId, setEditingRecordId] = useState<number | null>(null);
  const savedRecord = useRef<InvestmentDecisionRecord | null>(null);
  const impression = useRef(false);
  useEffect(() => {
    const controller = new AbortController();
    let started = false;
    function claim() {
      if (started || document.visibilityState === 'hidden') return;
      started = true;
      claimReviewReminder(requestKey, controller.signal)
        .then((response) => {
          if (!controller.signal.aborted) setRecords(response.transactions);
        })
        .catch(() => {
          // Failed eligibility checks must not invent a reminder or interrupt the page.
        });
    }
    claim();
    document.addEventListener('visibilitychange', claim);
    return () => {
      controller.abort();
      document.removeEventListener('visibilitychange', claim);
    };
  }, [requestKey]);
  const pending = records.filter((record) => {
    const current = providedRecords?.find((value) => value.id === record.id) ?? record;
    return (
      (current.reviews.length === 0 || record.id === editingRecordId) &&
      !completedIds.includes(record.id)
    );
  });
  useEffect(() => {
    if (!remind || !pending.length) return;
    function trackVisible() {
      if (document.visibilityState === 'hidden' || impression.current) return;
      impression.current = true;
      trackLeagueEvent('view_league_review_reminder', {
        surface: 'review_coach',
        entry_point: entryPoint,
        result_count: pending.length,
      });
    }
    trackVisible();
    document.addEventListener('visibilitychange', trackVisible);
    return () => document.removeEventListener('visibilitychange', trackVisible);
  }, [remind, pending.length, entryPoint]);
  const record = pending[0];
  if (!record) return null;
  return (
    <FloatingReviewCoach
      key={record.id}
      record={record}
      entryPoint={entryPoint}
      reminderCount={remind ? pending.length : undefined}
      onOpen={(target) => {
        setEditingRecordId(record.id);
        trackLeagueEvent('league_review_reminder_action', {
          surface: 'review_coach',
          entry_point: entryPoint,
          action: 'open_review',
          target,
          status: 'clicked',
        });
      }}
      onDefer={async (target) => {
        setRemind(false);
        try {
          await snoozeReviewReminder(requestKey);
          trackLeagueEvent('league_review_reminder_action', {
            surface: 'review_coach',
            entry_point: entryPoint,
            action: target === 'bubble' ? 'dismiss_reminder' : 'snooze_reminder',
            target,
            status: 'success',
          });
        } catch (error) {
          trackLeagueEvent('league_review_reminder_action', {
            surface: 'review_coach',
            entry_point: entryPoint,
            action: target === 'bubble' ? 'dismiss_reminder' : 'snooze_reminder',
            target,
            status: 'error',
            error_type: leagueErrorType(error),
          });
          throw error;
        }
      }}
      onSaved={(saved) => {
        savedRecord.current = saved;
      }}
      onDismiss={() => {
        const saved = savedRecord.current;
        if (saved?.id !== record.id) return;
        setCompletedIds((values) => [...values, saved.id]);
        setEditingRecordId(null);
        savedRecord.current = null;
        setRemind(false);
        onReviewed?.(saved);
      }}
    />
  );
}
