import { useEffect, useMemo, useRef, useState } from 'react';
import { trackEvent } from '../../../analytics';
import { analyzePortfolio, createPortfolioSnapshotKey } from '../api';
import {
  clearPortfolioAnalysis,
  loadPortfolioAnalysis,
  savePortfolioAnalysis,
} from '../portfolioAnalysisStorage';
import type {
  PortfolioAsset,
  PortfolioAnalysisResponse,
  PortfolioAssetImpactResponse,
  PortfolioImpactDirection,
  PortfolioImpactLevel,
} from '../types';
import '../portfolioAnalysis.css';

const DIRECTION_LABELS: Record<PortfolioImpactDirection, string> = {
  POSITIVE: '긍정',
  NEGATIVE: '부정',
  NEUTRAL: '중립',
};

const DIRECTION_IMPACT_LABELS: Record<PortfolioImpactDirection, string> = {
  POSITIVE: '긍정 영향',
  NEGATIVE: '부정 영향',
  NEUTRAL: '중립 영향',
};

const IMPACT_LEVEL_LABELS: Record<PortfolioImpactLevel, string> = {
  HIGH: '높음',
  MEDIUM: '보통',
  LOW: '낮음',
};

const DIRECTION_COLORS: Record<PortfolioImpactDirection, string> = {
  POSITIVE: '#ef464d',
  NEGATIVE: '#3e83d7',
  NEUTRAL: '#98a2b3',
};

const INACTIVE_ASSET_COLOR = '#e8eef8';
const DONUT_CENTER = 110;
const DONUT_RADIUS = 78;
const DONUT_SEGMENT_GAP = 4;

function formatImpactScore(score: number) {
  const sign = score > 0 ? '+' : score < 0 ? '−' : '';
  return `${sign}${Math.abs(score).toFixed(2)}점`;
}

function formatImpactMagnitude(score: number) {
  return `${Math.abs(score).toFixed(2)}점`;
}

function formatAnalyzedAt(analyzedAt: string | null) {
  if (!analyzedAt) return null;
  const date = new Date(analyzedAt);
  if (Number.isNaN(date.getTime())) return null;

  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  const hour = String(date.getHours()).padStart(2, '0');
  const minute = String(date.getMinutes()).padStart(2, '0');
  return `${year}.${month}.${day} ${hour}:${minute} 분석 기준`;
}

function normalizeAssetName(assetName: string) {
  return assetName.trim().toLocaleLowerCase();
}

function orderAssetsByImpact(
  assets: PortfolioAsset[],
  impactByAssetName: Map<string, PortfolioAssetImpactResponse>,
) {
  return [...assets].sort((left, right) => {
    const leftImpact = impactByAssetName.get(normalizeAssetName(left.assetName));
    const rightImpact = impactByAssetName.get(normalizeAssetName(right.assetName));

    if (leftImpact && rightImpact) return leftImpact.rank - rightImpact.rank;
    if (leftImpact) return -1;
    if (rightImpact) return 1;
    return 0;
  });
}

function getDonutPoint(percentage: number, radius: number) {
  const angle = (percentage / 100) * Math.PI * 2 - Math.PI / 2;
  return {
    x: DONUT_CENTER + radius * Math.cos(angle),
    y: DONUT_CENTER + radius * Math.sin(angle),
  };
}

function createDonutSegmentPath(start: number, percentage: number, thickness: number) {
  const outerRadius = DONUT_RADIUS + thickness / 2;
  const innerRadius = DONUT_RADIUS - thickness / 2;

  if (percentage >= 99.999) {
    const outerTop = getDonutPoint(0, outerRadius);
    const outerBottom = getDonutPoint(50, outerRadius);
    const innerTop = getDonutPoint(0, innerRadius);
    const innerBottom = getDonutPoint(50, innerRadius);

    return [
      `M ${outerTop.x} ${outerTop.y}`,
      `A ${outerRadius} ${outerRadius} 0 1 1 ${outerBottom.x} ${outerBottom.y}`,
      `A ${outerRadius} ${outerRadius} 0 1 1 ${outerTop.x} ${outerTop.y}`,
      `L ${innerTop.x} ${innerTop.y}`,
      `A ${innerRadius} ${innerRadius} 0 1 0 ${innerBottom.x} ${innerBottom.y}`,
      `A ${innerRadius} ${innerRadius} 0 1 0 ${innerTop.x} ${innerTop.y}`,
      'Z',
    ].join(' ');
  }

  const halfGap = DONUT_SEGMENT_GAP / 2;
  const outerInset = Math.min(
    (Math.asin(Math.min(halfGap / outerRadius, 1)) / (Math.PI * 2)) * 100,
    percentage / 2,
  );
  const innerInset = Math.min(
    (Math.asin(Math.min(halfGap / innerRadius, 1)) / (Math.PI * 2)) * 100,
    percentage / 2,
  );
  const end = start + percentage;
  const outerStart = getDonutPoint(start + outerInset, outerRadius);
  const outerEnd = getDonutPoint(end - outerInset, outerRadius);
  const innerEnd = getDonutPoint(end - innerInset, innerRadius);
  const innerStart = getDonutPoint(start + innerInset, innerRadius);
  const largeArc = percentage > 50 ? 1 : 0;

  return [
    `M ${outerStart.x} ${outerStart.y}`,
    `A ${outerRadius} ${outerRadius} 0 ${largeArc} 1 ${outerEnd.x} ${outerEnd.y}`,
    `L ${innerEnd.x} ${innerEnd.y}`,
    `A ${innerRadius} ${innerRadius} 0 ${largeArc} 0 ${innerStart.x} ${innerStart.y}`,
    'Z',
  ].join(' ');
}

function DirectionIcon({ direction }: { direction: PortfolioImpactDirection }) {
  if (direction === 'NEUTRAL') {
    return (
      <svg aria-hidden="true" viewBox="0 0 20 20">
        <path d="M3 10h13m-4-4 4 4-4 4" />
      </svg>
    );
  }

  return (
    <svg
      aria-hidden="true"
      className={direction === 'NEGATIVE' ? 'is-negative' : undefined}
      viewBox="0 0 20 20"
    >
      <path d="M10 17V3m-5 5 5-5 5 5" />
    </svg>
  );
}

function PortfolioEmpty() {
  return (
    <div className="portfolio-analysis-state portfolio-analysis-empty">
      <span className="portfolio-analysis-state-icon" aria-hidden="true">
        −
      </span>
      <p>
        포트폴리오에 자산을 등록하면 오늘의 주요 이슈가 내 자산에 미치는 영향을 확인할 수 있어요.
      </p>
    </div>
  );
}

function PortfolioAnalysisEmpty() {
  return (
    <div
      className="portfolio-analysis-state portfolio-analysis-empty"
      role="status"
      aria-live="polite"
    >
      <span className="portfolio-analysis-state-icon" aria-hidden="true">
        −
      </span>
      <strong>표시할 포트폴리오 분석 결과가 없어요.</strong>
      <p>잠시 후 다시 분석해 주세요.</p>
    </div>
  );
}

function PortfolioAnalysisLoading() {
  return (
    <div
      className="portfolio-analysis-state portfolio-analysis-empty"
      role="status"
      aria-live="polite"
    >
      <span className="portfolio-analysis-spinner" aria-hidden="true" />
      <strong>내 포트폴리오에 미치는 영향을 분석하고 있어요.</strong>
    </div>
  );
}

function PortfolioAnalysisError({ onRetry }: { onRetry: () => void }) {
  return (
    <div className="portfolio-analysis-state portfolio-analysis-empty" role="alert">
      <strong>포트폴리오 분석을 완료하지 못했어요.</strong>
      <button className="portfolio-analysis-retry-button" type="button" onClick={onRetry}>
        다시 시도
      </button>
    </div>
  );
}

type PortfolioDonutProps = {
  assets: PortfolioAsset[];
  impactByAssetName: Map<string, PortfolioAssetImpactResponse>;
  selectedImpact: PortfolioAssetImpactResponse;
  onSelect: (assetName: string) => void;
};

function PortfolioDonut({
  assets,
  impactByAssetName,
  selectedImpact,
  onSelect,
}: PortfolioDonutProps) {
  const orderedAssets = orderAssetsByImpact(assets, impactByAssetName);
  const totalWeight = orderedAssets.reduce((sum, asset) => sum + asset.weight, 0);
  let offset = 0;

  return (
    <div className="portfolio-analysis-donut-wrap">
      <svg
        className="portfolio-analysis-donut"
        viewBox="0 0 220 220"
        role="group"
        aria-label="전체 포트폴리오의 자산별 보유 비중"
      >
        {orderedAssets.map((asset) => {
          const impact = impactByAssetName.get(normalizeAssetName(asset.assetName));
          const percentage = totalWeight > 0 ? (asset.weight / totalWeight) * 100 : 0;
          const segmentOffset = offset;
          const isSelected = impact?.assetName === selectedImpact.assetName;
          const segmentThickness = isSelected ? 41 : 38;
          offset += percentage;

          return (
            <path
              aria-label={
                impact
                  ? `${asset.assetName}, 보유 비중 ${asset.weight}%, ${DIRECTION_LABELS[impact.direction]} 영향`
                  : `${asset.assetName}, 보유 비중 ${asset.weight}%, 분석 결과 없음`
              }
              aria-pressed={impact ? isSelected : undefined}
              className={`portfolio-donut-segment${impact ? ' is-interactive' : ''}${isSelected ? ' is-selected' : ''}`}
              d={createDonutSegmentPath(segmentOffset, percentage, segmentThickness)}
              data-thickness={segmentThickness}
              fill={
                isSelected && impact ? DIRECTION_COLORS[impact.direction] : INACTIVE_ASSET_COLOR
              }
              key={asset.assetName}
              onClick={impact ? () => onSelect(impact.assetName) : undefined}
              onKeyDown={
                impact
                  ? (event) => {
                      if (event.key === 'Enter' || event.key === ' ') {
                        event.preventDefault();
                        onSelect(impact.assetName);
                      }
                    }
                  : undefined
              }
              role={impact ? 'button' : undefined}
              tabIndex={impact ? 0 : -1}
            />
          );
        })}
      </svg>

      <div
        className={`portfolio-donut-center ${selectedImpact.direction.toLowerCase()}`}
        aria-live="polite"
      >
        <strong>{selectedImpact.assetName}</strong>
        <b>{`${selectedImpact.weight}%`}</b>
        <small>현재 보유 비중</small>
      </div>
    </div>
  );
}

type PortfolioAssetListProps = {
  assets: PortfolioAsset[];
  impactByAssetName: Map<string, PortfolioAssetImpactResponse>;
  selectedImpact: PortfolioAssetImpactResponse;
  onSelect: (assetName: string) => void;
};

function PortfolioAssetList({
  assets,
  impactByAssetName,
  selectedImpact,
  onSelect,
}: PortfolioAssetListProps) {
  const orderedAssets = orderAssetsByImpact(assets, impactByAssetName);

  return (
    <ul className="portfolio-asset-list" aria-label="포트폴리오 보유 자산">
      {orderedAssets.map((asset) => {
        const impact = impactByAssetName.get(normalizeAssetName(asset.assetName));
        const isSelected = impact?.assetName === selectedImpact.assetName;
        const rowContent = (
          <>
            <span
              className="portfolio-asset-dot"
              style={{
                backgroundColor:
                  isSelected && impact ? DIRECTION_COLORS[impact.direction] : INACTIVE_ASSET_COLOR,
              }}
              aria-hidden="true"
            />
            <span className="portfolio-asset-name">{asset.assetName}</span>
            <span
              className={`portfolio-asset-analysis${impact ? ` ${impact.direction.toLowerCase()}` : ''}`}
            >
              {impact
                ? `TOP ${impact.rank} · ${DIRECTION_IMPACT_LABELS[impact.direction]}`
                : '분석 결과 없음'}
            </span>
            <strong>{`${asset.weight}%`}</strong>
          </>
        );

        return (
          <li key={asset.assetName}>
            {impact ? (
              <button
                className={`portfolio-asset-row ${impact.direction.toLowerCase()}${isSelected ? ' is-selected' : ''}`}
                type="button"
                aria-pressed={isSelected}
                onClick={() => onSelect(impact.assetName)}
              >
                {rowContent}
              </button>
            ) : (
              <div className="portfolio-asset-row is-disabled">{rowContent}</div>
            )}
          </li>
        );
      })}
    </ul>
  );
}

function PortfolioImpactDetail({
  impact,
  analyzedAssetCount,
}: {
  impact: PortfolioAssetImpactResponse;
  analyzedAssetCount: number;
}) {
  return (
    <article className={`portfolio-impact-detail ${impact.direction.toLowerCase()}`}>
      <div className="portfolio-detail-header">
        <div>
          <span>{`포트폴리오 영향 TOP ${impact.rank} / ${analyzedAssetCount}`}</span>
          <h3>{impact.assetName}</h3>
        </div>
        <div className="portfolio-detail-badge-group">
          <span className="portfolio-direction-badge">
            <DirectionIcon direction={impact.direction} />
            {DIRECTION_IMPACT_LABELS[impact.direction]}
          </span>
          <small>{`영향 수준 ${IMPACT_LEVEL_LABELS[impact.impactLevel]}`}</small>
        </div>
      </div>

      <div className="portfolio-detail-copy">
        <p>
          {impact.issueSummary} 현재 보유 비중은 <strong>{`${impact.weight}%`}</strong>예요.
        </p>
        <p>{impact.expectedReaction}</p>
        <p>{impact.outlook}</p>
        <p>{impact.reason}</p>
      </div>

      <details className="portfolio-evidence">
        <summary>
          판단에 사용한 출처
          <span aria-hidden="true" />
        </summary>
        <blockquote>{impact.evidenceSentence}</blockquote>
        {impact.sources.length > 0 ? (
          <ul className="portfolio-evidence-sources">
            {impact.sources.map((source) => (
              <li key={source.url}>
                <a href={source.url} target="_blank" rel="noopener noreferrer">
                  {source.title}
                </a>
              </li>
            ))}
          </ul>
        ) : (
          <p>직접 연결된 출처가 없어요.</p>
        )}
      </details>
    </article>
  );
}

type PortfolioAnalysisProps = {
  assets: PortfolioAsset[];
};

export function PortfolioAnalysis({ assets }: PortfolioAnalysisProps) {
  const [analysis, setAnalysis] = useState<PortfolioAnalysisResponse | null>(null);
  const [analyzedAssets, setAnalyzedAssets] = useState<PortfolioAsset[] | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [selectedAssetName, setSelectedAssetName] = useState<string | null>(null);
  const requestIdRef = useRef(0);
  const portfolioSnapshotKey = createPortfolioSnapshotKey(assets);

  useEffect(() => {
    if (assets.length === 0) {
      requestIdRef.current += 1;
      clearPortfolioAnalysis();
      setAnalysis(null);
      setAnalyzedAssets(null);
      setError(null);
      setSelectedAssetName(null);
      setLoading(false);
      return;
    }
    if (analysis || analyzedAssets) return;

    requestIdRef.current += 1;
    const cachedAnalysis = loadPortfolioAnalysis();
    setAnalysis(cachedAnalysis);
    setAnalyzedAssets(
      cachedAnalysis
        ? cachedAnalysis.impacts.map(({ assetName, weight }) => ({ assetName, weight }))
        : null,
    );
    setError(null);
    setSelectedAssetName(null);
    setLoading(false);
  }, [analysis, analyzedAssets, assets, portfolioSnapshotKey]);

  const analyze = () => {
    const snapshot = assets.map((asset) => ({ ...asset }));
    if (snapshot.length === 0 || loading) return;
    trackEvent('start_portfolio_analysis');

    const requestId = requestIdRef.current + 1;
    requestIdRef.current = requestId;
    setAnalyzedAssets(snapshot);
    setAnalysis(null);
    setError(null);
    setSelectedAssetName(null);
    setLoading(true);

    analyzePortfolio(snapshot)
      .then((response) => {
        if (requestIdRef.current === requestId) {
          setAnalysis(response);
          savePortfolioAnalysis(snapshot, response);
        }
      })
      .catch((caughtError) => {
        if (requestIdRef.current === requestId) {
          setError(
            caughtError instanceof Error
              ? caughtError.message
              : '포트폴리오 분석을 불러오지 못했습니다.',
          );
        }
      })
      .finally(() => {
        if (requestIdRef.current === requestId) {
          setLoading(false);
        }
      });
  };

  const sortedImpacts = useMemo(
    () => [...(analysis?.impacts ?? [])].sort((left, right) => left.rank - right.rank),
    [analysis],
  );
  const impactByAssetName = useMemo(
    () => new Map(sortedImpacts.map((impact) => [normalizeAssetName(impact.assetName), impact])),
    [sortedImpacts],
  );
  const selectedImpact =
    sortedImpacts.find((impact) => impact.assetName === selectedAssetName) ?? sortedImpacts[0];
  const analyzedAtLabel = formatAnalyzedAt(analysis?.analyzedAt ?? null);

  const handleRetry = () => {
    analyze();
  };

  const hasAnalysis = Boolean(analysis && selectedImpact);
  const displayAssets = analyzedAssets ?? assets;
  const hasAnalysisTarget = displayAssets.length > 0;
  const canAnalyze = assets.length > 0 && !loading;

  return (
    <section className="portfolio-analysis-section" aria-labelledby="portfolio-ai-analysis-title">
      <div className={`portfolio-analysis-heading${hasAnalysis ? ' has-analysis' : ''}`}>
        <div>
          <h2 id="portfolio-ai-analysis-title">
            {hasAnalysis ? '오늘의 포트폴리오 분석' : '포트폴리오 AI 분석'}
          </h2>
          <p>
            {analysis && selectedImpact
              ? `오늘의 주요 이슈가 보유 자산 ${analysis.analyzedAssetCount}개에 미칠 영향을 분석했어요.`
              : '오늘의 경제·금융·산업 이슈가 보유 자산에 미칠 영향을 확인해 보세요.'}
          </p>
        </div>
        {!error ? (
          <button
            className="portfolio-analysis-button"
            type="button"
            disabled={!canAnalyze}
            onClick={analyze}
          >
            {loading ? '분석 중' : analysis ? '다시 분석하기' : '분석하기'}
          </button>
        ) : null}
      </div>

      {!hasAnalysisTarget ? <PortfolioEmpty /> : null}

      {hasAnalysisTarget && loading ? <PortfolioAnalysisLoading /> : null}

      {hasAnalysisTarget && !loading && error ? (
        <PortfolioAnalysisError onRetry={handleRetry} />
      ) : null}

      {hasAnalysisTarget && !loading && !error && analysis?.impacts.length === 0 ? (
        <PortfolioAnalysisEmpty />
      ) : null}

      {hasAnalysisTarget && !loading && !error && analysis && selectedImpact ? (
        <>
          <div className={`portfolio-overall-impact ${analysis.overallDirection.toLowerCase()}`}>
            <div className="portfolio-overall-score">
              <span>전체 포트폴리오 예상 영향</span>
              <strong>{`${DIRECTION_LABELS[analysis.overallDirection]} 영향 ${formatImpactMagnitude(analysis.overallScore)}`}</strong>
            </div>
            <div className="portfolio-overall-summary">
              <p>{analysis.overallImpact}</p>
              {analyzedAtLabel ? (
                <time dateTime={analysis.analyzedAt ?? undefined}>{analyzedAtLabel}</time>
              ) : null}
              <details className="portfolio-score-guide">
                <summary>영향 점수 산정 기준</summary>
                <p>
                  보유 비중과 자산별 영향 방향·수준을 합산한 -100~100 지표예요. 예상 수익률이나 상승
                  확률은 아닙니다.
                </p>
                <dl>
                  <div>
                    <dt>긍정 기여</dt>
                    <dd>{formatImpactScore(analysis.positiveImpactScore)}</dd>
                  </div>
                  <div>
                    <dt>부정 기여</dt>
                    <dd>{`−${Math.abs(analysis.negativeImpactScore).toFixed(2)}점`}</dd>
                  </div>
                  <div>
                    <dt>전체 영향</dt>
                    <dd>{formatImpactScore(analysis.overallScore)}</dd>
                  </div>
                </dl>
              </details>
            </div>
          </div>
          <div className="portfolio-analysis-layout">
            <div className="portfolio-analysis-overview">
              <div className="portfolio-analysis-overview-heading">
                <h3>내 포트폴리오</h3>
                <span className={selectedImpact.direction.toLowerCase()}>
                  {`${DIRECTION_LABELS[selectedImpact.direction]} 영향`}
                </span>
              </div>

              <PortfolioDonut
                assets={displayAssets}
                impactByAssetName={impactByAssetName}
                selectedImpact={selectedImpact}
                onSelect={setSelectedAssetName}
              />
              <PortfolioAssetList
                assets={displayAssets}
                impactByAssetName={impactByAssetName}
                selectedImpact={selectedImpact}
                onSelect={setSelectedAssetName}
              />
              <p className="portfolio-weight-caption">
                전체 포트폴리오에서 차지하는 현재 비중이에요.
              </p>
            </div>

            <PortfolioImpactDetail
              impact={selectedImpact}
              analyzedAssetCount={analysis.analyzedAssetCount}
            />
          </div>
        </>
      ) : null}
    </section>
  );
}
