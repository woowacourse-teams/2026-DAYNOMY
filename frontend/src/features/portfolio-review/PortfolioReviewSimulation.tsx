import { useEffect, useId, useRef, useState } from 'react';
import { ArrowCounterClockwiseIcon } from '@phosphor-icons/react/dist/csr/ArrowCounterClockwise';
import { LockSimpleIcon } from '@phosphor-icons/react/dist/csr/LockSimple';
import { SparkleIcon } from '@phosphor-icons/react/dist/csr/Sparkle';
import { XIcon } from '@phosphor-icons/react/dist/csr/X';
import { ApiError } from '../../api/client';
import { PortfolioReviewAnalysisResult } from './PortfolioReviewAnalysisResult';
import type { PortfolioAnalysisResponse } from '../portfolio/types';
import { analyzeReviewSimulation, getReviewSimulationAssets } from './api';
import type { PortfolioAnalysisComment } from './types';
import type { StockSearchItem } from '../stocks/types';
import { PortfolioReviewStockPicker } from './PortfolioReviewStockPicker';
import {
  SIMULATION_CATEGORIES,
  weightInBasisPoints,
  simulationWeights,
  analysisAssetName,
  simulationAllocation,
  type SimulationDraft,
} from './simulationAllocation';

type Props = { shareId: number; comment: PortfolioAnalysisComment };
const percent = (weight: number) => `${weight.toFixed(2)}%`;

export function PortfolioReviewSimulation(props: Props) {
  const [open, setOpen] = useState(false);
  const id = useId();
  return (
    <div className="portfolio-review-simulation">
      {!open ? (
        <button
          type="button"
          aria-expanded={false}
          aria-controls={id}
          onClick={() => setOpen(true)}
        >
          제안 반영해 보기
        </button>
      ) : (
        <SimulationEditor {...props} id={id} />
      )}
    </div>
  );
}

function SimulationEditor({ shareId, comment, id }: Props & { id: string }) {
  const [base, setBase] = useState<SimulationDraft[] | null>(null);
  const [draft, setDraft] = useState<SimulationDraft[]>([]);
  const [selectedStock, setSelectedStock] = useState<StockSearchItem | null>(null);
  const [weight, setWeight] = useState('');
  const [addedMessage, setAddedMessage] = useState('');
  const weightInput = useRef<HTMLInputElement>(null);
  const [loading, setLoading] = useState(true);
  const [attempt, setAttempt] = useState(0);
  const [pending, setPending] = useState(false);
  const [error, setError] = useState('');
  const [result, setResult] = useState<PortfolioAnalysisResponse | null>(null);

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
        const initial = assets.map(({ assetId, assetName, category }, index) => ({
          assetId,
          assetName,
          category,
          weight: allocation.weights[index].toFixed(2),
        }));
        setBase(initial);
        setDraft(initial);
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
  }, [shareId, comment.id, attempt]);

  const after = simulationWeights(draft);
  const valid = after?.total === 100;
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
  const canAdd =
    addition !== null &&
    analysisAssetName(addition).length <= 100 &&
    weightInBasisPoints(weight) !== null &&
    !duplicate &&
    draft.length < 50;

  function change(next: SimulationDraft[]) {
    setDraft(next);
    setResult(null);
    setError('');
    setAddedMessage('');
  }
  async function analyze() {
    if (!valid || !after || pending) return;
    setPending(true);
    setError('');
    setResult(null);
    try {
      setResult(
        await analyzeReviewSimulation(
          shareId,
          comment.id,
          draft.map((asset, index) => ({
            assetName: analysisAssetName(asset),
            weight: after.weights[index],
          })),
        ),
      );
    } catch (caught) {
      setError(
        caught instanceof ApiError
          ? caught.message
          : '임시 구성 분석에 실패했어요. 다시 시도해 주세요.',
      );
    } finally {
      setPending(false);
    }
  }

  return (
    <section id={id} aria-labelledby={`${id}-title`} className="portfolio-review-simulation-editor">
      <header>
        <h3 id={`${id}-title`}>제안 반영하기</h3>
        <p className="portfolio-simulation-intro">비중 합계를 100%로 맞춘 뒤 분석해 보세요.</p>
      </header>
      {loading ? (
        <p role="status">현재 구성을 불러오고 있어요.</p>
      ) : base ? (
        <form className="portfolio-simulation-form" onSubmit={(event) => event.preventDefault()}>
          <fieldset disabled={pending} className="portfolio-simulation-stock-addition">
            <legend>종목 추가</legend>
            <PortfolioReviewStockPicker
              value={selectedStock}
              onSelect={(stock) => {
                setSelectedStock(stock);
                setAddedMessage('');
                setWeight('');
              }}
              draft={draft}
              disabled={pending}
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
                      aria-invalid={weight !== '' && weightInBasisPoints(weight) === null}
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
                  : '추가한 뒤 전체 비중을 100%로 맞춰 주세요.'}
              </p>
            ) : null}
            <p className="portfolio-simulation-added" role="status">
              {addedMessage}
            </p>
          </fieldset>
          <fieldset
            disabled={pending}
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
                  {draft.map((asset) => {
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
                        <td data-label="비중 (%)" className="portfolio-simulation-edit-weight">
                          <span className="portfolio-simulation-weight-input">
                            <input
                              aria-label={`${asset.assetName} 비중`}
                              aria-invalid={weightInBasisPoints(asset.weight) === null}
                              type="number"
                              inputMode="decimal"
                              min="0.01"
                              max="100"
                              step="0.01"
                              value={asset.weight}
                              onChange={(event) =>
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
              <p role="status">비중 합계를 100%로 맞춰 주세요. 현재 {percent(after.total)}예요.</p>
            ) : null}
          </fieldset>
          <div className="portfolio-simulation-footer">
            <p className="portfolio-simulation-help portfolio-simulation-privacy">
              <LockSimpleIcon size={16} aria-hidden="true" />
              실제 포트폴리오는 그대로이며, 결과는 나만 볼 수 있어요.
            </p>
            <div className="portfolio-simulation-footer-actions">
              <button
                type="button"
                className="portfolio-simulation-reset"
                disabled={pending}
                onClick={() => change(base)}
              >
                <ArrowCounterClockwiseIcon size={16} aria-hidden="true" />
                원래 구성으로 되돌리기
              </button>
              <button
                className="portfolio-simulation-submit"
                type="button"
                onClick={() => void analyze()}
                disabled={!valid || pending}
              >
                <SparkleIcon size={18} aria-hidden="true" />
                {pending ? '임시 구성 분석 중…' : '이 구성으로 포트폴리오 분석'}
              </button>
            </div>
          </div>
          {pending ? (
            <p role="status" className="portfolio-simulation-progress">
              분석 중이에요. 결과가 나올 때까지 잠시 기다려 주세요.
            </p>
          ) : null}
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
      {result ? (
        <div className="portfolio-simulation-result">
          <PortfolioReviewAnalysisResult analysis={result} title="임시 포트폴리오 분석 결과" />
        </div>
      ) : null}
    </section>
  );
}
