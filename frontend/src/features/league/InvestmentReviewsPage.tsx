import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { getInvestmentDecisions } from './api';
import { leagueErrorType, trackLeagueEvent } from './analytics';
import type { InvestmentDecisionRecord } from './types';
import { InvestmentRecords } from './InvestmentRecords';
import './league.css';
import './shared-portfolio.css';
import { useLeaguePageHeading } from './useLeaguePageHeading';

export function InvestmentReviewsPage() {
  const heading = useLeaguePageHeading('투자 복기');
  const [records, setRecords] = useState<InvestmentDecisionRecord[] | null>(null);
  const [loadError, setLoadError] = useState('');
  const [retry, setRetry] = useState(0);
  const [reviewId, setReviewId] = useState<number | null>(null);
  useEffect(() => {
    const controller = new AbortController();
    setLoadError('');
    getInvestmentDecisions(controller.signal)
      .then((values) => {
        if (!controller.signal.aborted) {
          setRecords(values);
          trackLeagueEvent('league_load_result', {
            surface: 'reviews',
            status: 'success',
            result_count: values.length,
          });
        }
      })
      .catch((caught: unknown) => {
        if (!controller.signal.aborted) {
          setLoadError('투자 기록을 불러오지 못했습니다.');
          trackLeagueEvent('league_load_result', {
            surface: 'reviews',
            status: 'error',
            error_type: leagueErrorType(caught),
          });
        }
      });
    return () => controller.abort();
  }, [retry]);
  return (
    <main className="league-page shared-page league-reviews-page">
      <header className="league-hero">
        <h1 ref={heading} tabIndex={-1}>
          투자 복기
        </h1>
        <Link className="league-text-link" to="/league/portfolio">
          {records?.length === 0 ? '보유 이유 작성하기' : '종목 관리'}
        </Link>
      </header>
      {loadError ? (
        <div className="league-state" role="alert">
          <p>{loadError}</p>
          <button
            onClick={() => {
              trackLeagueEvent('league_action', {
                surface: 'reviews',
                action: 'retry',
                status: 'clicked',
              });
              setRetry((value) => value + 1);
            }}
          >
            다시 불러오기
          </button>
        </div>
      ) : records === null ? (
        <p className="league-state" role="status">
          투자 기록을 불러오는 중입니다.
        </p>
      ) : records.length === 0 ? (
        <section className="league-empty" role="status">
          <strong>아직 투자 기록이 없습니다</strong>
        </section>
      ) : (
        <section className="league-review-list" aria-label="복기할 투자 기록">
          <InvestmentRecords
            records={records}
            surface="reviews"
            reviewId={reviewId}
            setReviewId={setReviewId}
            onRecordChange={(saved) =>
              setRecords((values) =>
                (values ?? []).map((record) => (record.id === saved.id ? saved : record)),
              )
            }
          />
        </section>
      )}
    </main>
  );
}
