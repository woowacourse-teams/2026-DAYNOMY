import { useEffect, useId, useRef, useState, type ReactNode } from 'react';
import { ArrowCounterClockwiseIcon } from '@phosphor-icons/react/dist/csr/ArrowCounterClockwise';
import { ArrowRightIcon } from '@phosphor-icons/react/dist/csr/ArrowRight';
import { CaretRightIcon } from '@phosphor-icons/react/dist/csr/CaretRight';
import { LockSimpleIcon } from '@phosphor-icons/react/dist/csr/LockSimple';
import { SparkleIcon } from '@phosphor-icons/react/dist/csr/Sparkle';
import { XIcon } from '@phosphor-icons/react/dist/csr/X';
import { ApiError } from '../../api/client';
import { PortfolioDialog } from '../portfolio/components/PortfolioDialog';
import { PortfolioReviewAnalysisResult } from './PortfolioReviewAnalysisResult';
import type { PortfolioAnalysisResponse } from '../portfolio/types';
import {
  analyzeReviewSimulation,
  getReviewSimulationAssets,
  savePortfolioReviewProposal,
} from './api';
import type { PortfolioAnalysisComment, PortfolioReviewProposal } from './types';
import type { StockSearchItem } from '../stocks/types';
import { PortfolioReviewStockPicker } from './PortfolioReviewStockPicker';
import {
  SIMULATION_CATEGORIES,
  balanceSimulationDraft,
  weightInBasisPoints,
  simulationWeights,
  analysisAssetName,
  simulationAllocation,
  type SimulationDraft,
} from './simulationAllocation';

export type PortfolioReviewResultState = {
  proposal: PortfolioReviewProposal;
  base: SimulationDraft[] | null;
  draft: SimulationDraft[];
  analysis: PortfolioAnalysisResponse | null;
};

type Props = {
  shareId: number;
  comment: PortfolioAnalysisComment;
  proposal: PortfolioReviewProposal | null;
  onProposalSaved: (proposal: PortfolioReviewProposal) => void;
  onAnalysisComplete: (result: PortfolioReviewResultState) => void;
};
const percent = (weight: number) => `${weight.toFixed(2)}%`;
const directionLabels = {
  POSITIVE: '긍정 영향',
  NEGATIVE: '부정 영향',
  NEUTRAL: '중립 영향',
} as const;
const directionLabel = (direction: PortfolioAnalysisResponse['overallDirection']) =>
  direction ? directionLabels[direction] : '판단 보류';
const impactScore = (value: number | null) =>
  value === null
    ? '판단 보류'
    : `${value > 0 ? '+' : value < 0 ? '−' : ''}${Math.abs(value).toFixed(2)}점`;

export function PortfolioReviewSimulation(props: Props) {
  const [open, setOpen] = useState(false);
  const id = useId();
  return (
    <div className="portfolio-review-simulation">
      <button
        id={`portfolio-review-simulation-${props.comment.id}`}
        type="button"
        className="portfolio-review-simulation-trigger"
        aria-haspopup="dialog"
        aria-expanded={open}
        aria-controls={open ? id : undefined}
        onClick={() => setOpen(true)}
      >
        <SparkleIcon size={18} aria-hidden="true" />
        <span>
          <strong>{props.proposal ? '저장한 개선안 수정' : '이 의견으로 개선안 만들기'}</strong>
          <small>
            {props.proposal
              ? '저장한 구성을 수정하거나 다시 분석할 수 있어요.'
              : '실제 포트폴리오는 바뀌지 않아요.'}
          </small>
        </span>
        <CaretRightIcon size={16} weight="bold" aria-hidden="true" />
      </button>
      {open ? (
        <PortfolioDialog
          titleId={`${id}-title`}
          onClose={() => setOpen(false)}
          className="portfolio-review-simulation-dialog"
        >
          <SimulationEditor {...props} id={id} onClose={() => setOpen(false)} />
        </PortfolioDialog>
      ) : null}
    </div>
  );
}

function SimulationEditor({
  shareId,
  comment,
  proposal,
  onProposalSaved,
  onAnalysisComplete,
  id,
  onClose,
}: Props & { id: string; onClose: () => void }) {
  const [base, setBase] = useState<SimulationDraft[] | null>(null);
  const [draft, setDraft] = useState<SimulationDraft[]>([]);
  const [selectedStock, setSelectedStock] = useState<StockSearchItem | null>(null);
  const [weight, setWeight] = useState('');
  const [addedMessage, setAddedMessage] = useState('');
  const weightInput = useRef<HTMLInputElement>(null);
  const [loading, setLoading] = useState(true);
  const [attempt, setAttempt] = useState(0);
  const [pending, setPending] = useState<'saving' | 'analyzing' | null>(null);
  const [error, setError] = useState('');
  const title = '개선안 편집';
  const initialProposal = useRef(proposal).current;

  useEffect(() => {
    if (selectedStock) weightInput.current?.focus();
  }, [selectedStock]);

  useEffect(() => {
    const controller = new AbortController();
    setLoading(true);
    setError('');
    setAddedMessage('');
    getReviewSimulationAssets(shareId, comment.id, controller.signal)
      .then((assets) => {
        if (controller.signal.aborted) return;
        const allocation = simulationAllocation(
          assets.map((asset) => ({ amount: asset.amount.toFixed(2) })),
        );
        if (!allocation) throw new Error('현재 비중을 계산하지 못했어요.');
        const initial = balanceSimulationDraft(
          assets.map(({ assetId, assetName, category }, index) => ({
            assetId,
            assetName,
            category,
            weight: allocation.weights[index].toFixed(2),
          })),
        );
        if (!initial) throw new Error('현재 비중을 계산하지 못했어요.');
        setBase(initial);
        const proposalDraft = initialProposal
          ? initialProposal.assets.map(({ assetId, assetName, category, weight }) => ({
              assetId,
              assetName,
              category,
              weight: weight.toFixed(2),
            }))
          : initial;
        setDraft(balanceSimulationDraft(proposalDraft) ?? proposalDraft);
      })
      .catch((caught: unknown) => {
        if (!controller.signal.aborted)
          setError(
            caught instanceof ApiError ? caught.message : '현재 포트폴리오를 불러오지 못했어요.',
          );
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });
    return () => controller.abort();
  }, [shareId, comment.id, initialProposal, attempt]);

  const after = simulationWeights(draft);
  const balancedDraft = balanceSimulationDraft(draft);
  const valid = balancedDraft !== null && after?.total === 100;
  const addition: SimulationDraft | null = selectedStock
    ? {
        assetId: selectedStock.assetId,
        assetName: selectedStock.name,
        category: selectedStock.category,
        weight,
      }
    : null;
  const duplicate = draft.some(
    (asset) =>
      addition &&
      (asset.assetId === addition.assetId ||
        analysisAssetName(asset).toLowerCase() === analysisAssetName(addition).toLowerCase()),
  );
  const balancedAddition = addition ? balanceSimulationDraft([...draft, addition]) : null;
  const automaticUnits = balancedDraft ? weightInBasisPoints(balancedDraft[0].weight) : null;
  const maximumAdditionalWeight =
    automaticUnits === null ? 0 : Math.max(0, (automaticUnits - 1) / 100);
  const canAdd =
    addition !== null &&
    analysisAssetName(addition).length <= 100 &&
    weightInBasisPoints(weight) !== null &&
    balancedAddition !== null &&
    !duplicate &&
    draft.length < 50;

  function change(next: SimulationDraft[]) {
    setDraft(next.length ? (balanceSimulationDraft(next) ?? next) : []);
    setError('');
    setAddedMessage('');
  }
  async function analyze() {
    if (!valid || !after || pending) return;
    let proposalSaved = false;
    setPending('saving');
    setError('');
    try {
      const proposal = await savePortfolioReviewProposal(
        shareId,
        comment.id,
        draft.map((asset, index) => ({
          assetId: asset.assetId,
          weight: after.weights[index],
        })),
      );
      proposalSaved = true;
      onProposalSaved(proposal);
      setPending('analyzing');
      const analysis = await analyzeReviewSimulation(
        shareId,
        comment.id,
        draft.map((asset, index) => ({
          assetName: analysisAssetName(asset),
          weight: after.weights[index],
        })),
      );
      onAnalysisComplete({ proposal, base, draft, analysis });
      onClose();
    } catch (caught) {
      if (proposalSaved) {
        setError('개선안은 저장했지만 분석에 실패했어요. 저장된 구성으로 다시 시도해 주세요.');
      } else {
        setError(caught instanceof ApiError ? caught.message : '개선안을 저장하지 못했어요.');
      }
    } finally {
      setPending(null);
    }
  }

  return (
    <section id={id} aria-labelledby={`${id}-title`} className="portfolio-review-simulation-editor">
      <header>
        <h3 id={`${id}-title`} tabIndex={-1}>
          {title}
        </h3>
        <p className="portfolio-simulation-intro">
          다른 종목의 비중을 입력하면 첫 번째 종목이 자동으로 조정돼요.
        </p>
        <button
          type="button"
          className="portfolio-simulation-close"
          aria-label={`${title} 닫기`}
          onClick={onClose}
        >
          <XIcon size={20} aria-hidden="true" />
        </button>
      </header>
      {loading ? (
        <p role="status">현재 구성을 불러오고 있어요.</p>
      ) : base ? (
        <form className="portfolio-simulation-form" onSubmit={(event) => event.preventDefault()}>
          <fieldset disabled={pending !== null} className="portfolio-simulation-stock-addition">
            <legend>종목 추가</legend>
            <PortfolioReviewStockPicker
              value={selectedStock}
              onSelect={(stock) => {
                setSelectedStock(stock);
                setAddedMessage('');
                setWeight('');
              }}
              draft={draft}
              disabled={pending !== null}
            />
            {selectedStock ? (
              <div className="portfolio-simulation-add">
                <label>
                  추가할 비중 (%)
                  <span className="portfolio-simulation-weight-input">
                    <input
                      ref={weightInput}
                      aria-label="추가할 비중 (%)"
                      aria-describedby={`${id}-weight-help`}
                      aria-invalid={
                        weight !== '' &&
                        (weightInBasisPoints(weight) === null || balancedAddition === null)
                      }
                      type="number"
                      inputMode="decimal"
                      placeholder="예: 20"
                      min="0.01"
                      max="100"
                      step="0.01"
                      value={weight}
                      onChange={(event) => setWeight(event.target.value)}
                      onKeyDown={(event) => {
                        if (event.key === 'Enter') event.preventDefault();
                      }}
                    />
                    <span aria-hidden="true">%</span>
                  </span>
                </label>
                <button
                  type="button"
                  className="portfolio-simulation-add-button"
                  disabled={!canAdd}
                  onClick={() => {
                    if (!canAdd || !addition) return;
                    change([...draft, addition]);
                    setAddedMessage(`${addition.assetName} 추가 완료`);
                    setSelectedStock(null);
                    setWeight('');
                  }}
                >
                  임시 구성에 추가
                </button>
              </div>
            ) : null}
            {duplicate ? (
              <p role="status">이미 있는 자산이에요. 아래 비중을 조정해 주세요.</p>
            ) : null}
            {selectedStock ? (
              <p id={`${id}-weight-help`} className="portfolio-simulation-help">
                {weight !== '' && weightInBasisPoints(weight) === null
                  ? '0% 초과, 100% 이하로 소수점 두 자리까지 입력해 주세요.'
                  : weight !== '' && balancedAddition === null
                    ? `입력 가능한 비율은 최대 ${maximumAdditionalWeight.toFixed(2)}%예요.`
                    : '추가하면 첫 번째 종목 비율이 자동으로 조정돼요.'}
              </p>
            ) : null}
            <p className="portfolio-simulation-added" role="status">
              {addedMessage}
            </p>
          </fieldset>
          <fieldset
            disabled={pending !== null}
            className="portfolio-simulation-composition"
            aria-labelledby={`${id}-composition-title`}
          >
            <div className="portfolio-simulation-composition-heading">
              <h4 id={`${id}-composition-title`}>구성 종목</h4>
              <div className="portfolio-simulation-totals" aria-live="polite">
                <span>
                  합계 <strong>{after ? percent(after.total) : '입력 확인'}</strong>
                </span>
              </div>
            </div>
            <div className="portfolio-simulation-table-wrap">
              <table className="portfolio-simulation-table">
                <thead>
                  <tr>
                    <th>자산</th>
                    <th>비중 (%)</th>
                    <th aria-label="자산 삭제" />
                  </tr>
                </thead>
                <tbody>
                  {draft.map((asset, index) => {
                    const baseIndex = base.findIndex(
                      (value) => analysisAssetName(value) === analysisAssetName(asset),
                    );
                    return (
                      <tr key={analysisAssetName(asset)}>
                        <th scope="row">
                          <strong>{asset.assetName}</strong>
                          <small>
                            {SIMULATION_CATEGORIES[asset.category]}
                            {baseIndex < 0 ? ' · 추가' : ''}
                          </small>
                        </th>
                        <td
                          data-label="비중 (%)"
                          className={`portfolio-simulation-edit-weight${index === 0 ? ' is-automatic' : ''}`}
                        >
                          <span className="portfolio-simulation-weight-input">
                            <input
                              aria-label={`${asset.assetName} 비중`}
                              aria-describedby={index === 0 ? `${id}-automatic-help` : undefined}
                              aria-invalid={weightInBasisPoints(asset.weight) === null}
                              type="number"
                              inputMode="decimal"
                              min="0.01"
                              max="100"
                              step="0.01"
                              value={asset.weight}
                              readOnly={index === 0}
                              onChange={
                                index === 0
                                  ? undefined
                                  : (event) =>
                                      change(
                                        draft.map((value) =>
                                          value === asset
                                            ? { ...value, weight: event.target.value }
                                            : value,
                                        ),
                                      )
                              }
                            />
                            <span aria-hidden="true">%</span>
                          </span>
                          {index === 0 ? (
                            <small className="portfolio-simulation-automatic-badge">
                              자동 조정
                            </small>
                          ) : null}
                        </td>
                        <td className="portfolio-simulation-remove">
                          <button
                            type="button"
                            aria-label={`${asset.assetName} 삭제`}
                            onClick={() => change(draft.filter((value) => value !== asset))}
                          >
                            <XIcon size={16} aria-hidden="true" />
                          </button>
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
            {!draft.length ? (
              <p>분석할 자산을 하나 이상 추가해 주세요.</p>
            ) : !after ? (
              <p role="status">비중은 0% 초과, 100% 이하로 소수점 두 자리까지 입력해 주세요.</p>
            ) : !valid ? (
              <p role="status">자동 조정 종목의 비중을 최소 0.01% 남겨 주세요.</p>
            ) : null}
            {draft.length ? (
              <p id={`${id}-automatic-help`} className="portfolio-simulation-automatic-help">
                첫 번째 종목이 나머지 비율을 채워 합계를 100%로 유지해요.
              </p>
            ) : null}
          </fieldset>
          <div className="portfolio-simulation-footer">
            <p className="portfolio-simulation-help portfolio-simulation-privacy">
              <LockSimpleIcon size={16} aria-hidden="true" />
              개선안만 저장돼요. 실제 포트폴리오와 분석 결과는 저장되지 않아요.
            </p>
            <div className="portfolio-simulation-footer-actions">
              <button
                type="button"
                className="portfolio-simulation-reset"
                disabled={pending !== null}
                onClick={() => change(base)}
              >
                <ArrowCounterClockwiseIcon size={16} aria-hidden="true" />
                원래 구성으로 되돌리기
              </button>
              <button
                className="portfolio-simulation-submit"
                type="button"
                onClick={() => void analyze()}
                disabled={!valid || pending !== null}
              >
                <SparkleIcon size={18} aria-hidden="true" />
                {pending === 'saving'
                  ? '개선안 저장 중…'
                  : pending === 'analyzing'
                    ? '개선안 분석 중…'
                    : '개선안 저장하고 분석'}
              </button>
            </div>
            {pending ? (
              <div role="status" className="portfolio-simulation-progress">
                {pending === 'saving'
                  ? '개선안을 저장하고 있어요.'
                  : '분석 중이에요. 결과가 나올 때까지 잠시 기다려 주세요.'}
              </div>
            ) : null}
          </div>
        </form>
      ) : (
        <button type="button" onClick={() => setAttempt(attempt + 1)}>
          다시 불러오기
        </button>
      )}
      {error ? (
        <p role="alert" className="portfolio-share-error">
          {error}
        </p>
      ) : null}
    </section>
  );
}

export function PortfolioReviewResult({
  base,
  draft,
  analysis,
  proposal,
  onReanalyze,
  onDelete,
  onAnalyze,
  deleting,
  analyzing,
  baselineAnalysis,
  sourceComment,
  proposalSelector,
}: PortfolioReviewResultState & {
  onReanalyze: () => void;
  onDelete: () => void;
  onAnalyze: () => void;
  deleting: boolean;
  analyzing: boolean;
  baselineAnalysis?: PortfolioAnalysisResponse | null;
  sourceComment?: string | null;
  proposalSelector?: ReactNode;
}) {
  const id = useId();
  const draftNames = new Set(draft.map(analysisAssetName));
  const assets = [
    ...draft,
    ...(base ?? []).filter((asset) => !draftNames.has(analysisAssetName(asset))),
  ];

  return (
    <section
      className={`portfolio-public-proposal-result${base ? '' : ' is-saved-only'}`}
      aria-labelledby={`${id}-section-title`}
      tabIndex={-1}
    >
      <header className="portfolio-public-proposal-heading">
        <div>
          <h2 id={`${id}-section-title`}>댓글 의견 반영 결과</h2>
          {sourceComment ? (
            <p className="portfolio-public-proposal-source">“{sourceComment}”</p>
          ) : null}
          <p>
            {new Intl.DateTimeFormat('ko-KR', { dateStyle: 'medium', timeStyle: 'short' }).format(
              new Date(proposal.updatedAt),
            )}{' '}
            개선안 저장 · 실제 포트폴리오에는 미반영
          </p>
        </div>
        <div className="portfolio-public-proposal-tools">
          {proposalSelector}
          <div className="portfolio-public-proposal-actions">
            {analysis ? (
              <button type="button" className="is-primary" onClick={onAnalyze} disabled={analyzing}>
                <SparkleIcon size={16} aria-hidden="true" />
                {analyzing ? '분석 중…' : '다시 분석'}
              </button>
            ) : null}
            <button type="button" onClick={onReanalyze} disabled={analyzing}>
              <ArrowCounterClockwiseIcon size={16} aria-hidden="true" />
              개선안 수정
            </button>
            <button
              type="button"
              className="is-danger"
              aria-label="개선안 삭제"
              onClick={onDelete}
              disabled={deleting || analyzing}
            >
              {deleting ? '삭제 중…' : '삭제'}
            </button>
          </div>
        </div>
      </header>
      <section className="portfolio-simulation-snapshot" aria-labelledby={`${id}-title`}>
        <header>
          <div>
            <h3 id={`${id}-title`}>변경한 구성</h3>
            <p>
              {base
                ? '현재 포트폴리오와 개선안의 비중 차이예요.'
                : '저장한 개선안이에요. 실제 포트폴리오에는 반영되지 않았어요.'}
            </p>
          </div>
        </header>
        <ul aria-label={base ? '현재 구성과 개선안 구성 비교' : '저장한 개선안 구성'}>
          {assets.map((asset) => {
            const name = analysisAssetName(asset);
            const current = percentage(base?.find((item) => analysisAssetName(item) === name));
            const proposed = percentage(draft.find((item) => analysisAssetName(item) === name));
            const difference = proposed - current;
            return (
              <li key={name}>
                <strong>{name}</strong>
                <div className="portfolio-simulation-snapshot-values">
                  {base ? (
                    <>
                      <span>
                        <small>현재</small>
                        <b>{percent(current)}</b>
                      </span>
                      <ArrowRightIcon size={16} aria-hidden="true" />
                    </>
                  ) : null}
                  <span>
                    <small>{base ? '개선안' : '비중'}</small>
                    <b>{percent(proposed)}</b>
                  </span>
                </div>
                {base ? (
                  <span
                    className={`portfolio-simulation-difference${difference === 0 ? ' is-unchanged' : ''}`}
                  >
                    {difference === 0
                      ? '변화 없음'
                      : `${difference > 0 ? '+' : '−'}${Math.abs(difference).toFixed(2)}%p`}
                  </span>
                ) : null}
              </li>
            );
          })}
        </ul>
        <p className="portfolio-simulation-snapshot-note">
          <LockSimpleIcon size={16} aria-hidden="true" />
          실제 보유 구성은 변경되지 않았고, 분석 결과는 저장하지 않았어요.
        </p>
      </section>
      {!analysis ? (
        <section
          className="portfolio-public-proposal-analysis-empty"
          aria-labelledby={`${id}-analysis-empty-title`}
        >
          <div>
            <h3 id={`${id}-analysis-empty-title`}>AI 분석을 다시 실행해 주세요</h3>
            <p>개선안 구성은 저장되어 있어 편집하지 않고 바로 분석할 수 있어요.</p>
          </div>
          <button type="button" onClick={onAnalyze} disabled={analyzing}>
            <SparkleIcon size={18} aria-hidden="true" />
            {analyzing ? 'AI 분석 중…' : '저장된 개선안 다시 분석'}
          </button>
        </section>
      ) : null}
      {analysis ? (
        <div className="portfolio-simulation-result">
          <PortfolioAnalysisComparison baseline={baselineAnalysis ?? null} proposal={analysis} />
          <PortfolioReviewAnalysisResult
            analysis={analysis}
            title="이 구성의 AI 분석"
            portfolioLabel="개선안 포트폴리오"
            contextNote="실제 보유에는 반영되지 않은 개선안 분석이에요."
            fullLayout
          />
        </div>
      ) : null}
    </section>
  );
}

function PortfolioAnalysisComparison({
  baseline,
  proposal,
}: {
  baseline: PortfolioAnalysisResponse | null;
  proposal: PortfolioAnalysisResponse;
}) {
  const id = useId();
  if (!baseline) return null;
  const scoreChange =
    baseline.overallScore === null || proposal.overallScore === null
      ? null
      : proposal.overallScore - baseline.overallScore;
  return (
    <section className="portfolio-simulation-analysis-comparison" aria-labelledby={id}>
      <header>
        <h3 id={id}>공유 시점 대비 분석 변화</h3>
        <p>분석 시점과 근거가 달라 변화값은 참고용으로 확인해 주세요.</p>
      </header>
      <dl>
        <div>
          <dt>예상 영향</dt>
          <dd>
            <span>
              <small>공유 시점</small>
              <strong>{directionLabel(baseline.overallDirection)}</strong>
            </span>
            <ArrowRightIcon size={16} aria-hidden="true" />
            <span>
              <small>제안 구성</small>
              <strong>{directionLabel(proposal.overallDirection)}</strong>
            </span>
          </dd>
        </div>
        <div>
          <dt>영향 점수</dt>
          <dd>
            <span>
              <small>공유 시점</small>
              <strong>{impactScore(baseline.overallScore)}</strong>
            </span>
            <ArrowRightIcon size={16} aria-hidden="true" />
            <span>
              <small>제안 구성</small>
              <strong>{impactScore(proposal.overallScore)}</strong>
              {scoreChange !== null ? (
                <small className="portfolio-simulation-score-change">
                  {scoreChange > 0 ? '+' : scoreChange < 0 ? '−' : ''}
                  {Math.abs(scoreChange).toFixed(2)}점 변화
                </small>
              ) : null}
            </span>
          </dd>
        </div>
      </dl>
    </section>
  );
}

function percentage(asset: SimulationDraft | undefined) {
  return asset ? (weightInBasisPoints(asset.weight) ?? 0) / 100 : 0;
}
