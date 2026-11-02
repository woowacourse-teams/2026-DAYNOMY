import { useEffect, useRef, useState } from 'react';
import { getInvestmentDecisions } from './api';
import { FloatingReviewCoach } from './FloatingReviewCoach';
import type { InvestmentDecisionRecord } from './types';

export function LeagueReviewReminder({
  records: providedRecords,
  onReviewed,
}: {
  records?: InvestmentDecisionRecord[] | null;
  onReviewed?: (record: InvestmentDecisionRecord) => void;
}) {
  const [records, setRecords] = useState<InvestmentDecisionRecord[]>([]);
  const [remind, setRemind] = useState(true);
  const [completedIds, setCompletedIds] = useState<number[]>([]);
  const savedRecord = useRef<InvestmentDecisionRecord | null>(null);
  useEffect(() => {
    if (providedRecords !== undefined) return;
    const controller = new AbortController();
    getInvestmentDecisions(controller.signal)
      .then((values) => {
        if (!controller.signal.aborted) setRecords(values);
      })
      .catch(() => {
        /* A failed lookup must not invent an unfinished review. */
      });
    return () => controller.abort();
  }, [providedRecords]);
  const pending = (providedRecords === undefined ? records : (providedRecords ?? [])).filter(
    (record) => record.reviews.length === 0 && !completedIds.includes(record.id),
  );
  const record = pending[0];
  if (!record) return null;
  return (
    <FloatingReviewCoach
      key={record.id}
      record={record}
      reminderCount={remind ? pending.length : undefined}
      onSaved={(saved) => {
        savedRecord.current = saved;
      }}
      onDismiss={() => {
        const saved = savedRecord.current;
        if (saved?.id !== record.id) return;
        setCompletedIds((values) => [...values, saved.id]);
        savedRecord.current = null;
        setRemind(false);
        onReviewed?.(saved);
      }}
    />
  );
}
