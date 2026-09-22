import { useEffect, useMemo, useRef, useState } from 'react';
import { getPortfolioAnalysis, retryPortfolioAnalysis } from '../api';
import type {
  PortfolioAnalysisResponse,
  PortfolioAsset,
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

function createPortfolioSnapshotKey(assets: PortfolioAsset[]) {
  return JSON.stringify(
    assets
      .map((asset) => ({ assetName: normalizeAssetName(asset.assetName), weight: asset.weight }))
      .sort((left, right) => left.assetName.localeCompare(right.assetName)),
  );
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
      <p>포트폴리오에 자산을 등록하면 이 뉴스가 내 자산에 미치는 영향을 확인할 수 있어요.</p>
    </div>
  );
}

function PortfolioLoading() {
  return (
    <div className="portfolio-analysis-state portfolio-analysis-empty" role="status">
      <strong>포트폴리오 정보를 불러오고 있어요.</strong>
    </div>
  );
}

function PortfolioLoadError() {
  return (
    <div className="portfolio-analysis-state portfolio-analysis-empty" role="alert">
      <strong>포트폴리오 정보를 불러오지 못했어요.</strong>
      <p>잠시 후 페이지를 다시 열어 주세요.</p>
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
      <strong>이 뉴스와 직접 관련된 보유 자산이 없어요.</strong>
      <p>현재 포트폴리오에서 분석할 수 있는 직접적인 영향이 확인되지 않았어요.</p>
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
          const segmentThickness = isSelected ? 41 : impact ? 38 : 35;
          offset += percentage;

          return (
            <path
              aria-label={
                impact
                  ? `${asset.assetName}, 보유 비중 ${asset.weight}%, ${DIRECTION_LABELS[impact.direction]} 영향`
                  : `${asset.assetName}, 보유 비중 ${asset.weight}%, 상세 분석 제외`
              }
              aria-pressed={impact ? isSelected : undefined}
              className={`portfolio-donut-segment${impact ? ' is-interactive' : ''}${isSelected ? ' is-selected' : ''}`}
              d={createDonutSegmentPath(segmentOffset, percentage, segmentThickness)}
              data-thickness={segmentThickness}
              fill={impact ? DIRECTION_COLORS[impact.direction] : INACTIVE_ASSET_COLOR}
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
        <span>{`뉴스 영향 TOP ${selectedImpact.rank}`}</span>
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
                backgroundColor: impact ? DIRECTION_COLORS[impact.direction] : INACTIVE_ASSET_COLOR,
              }}
              aria-hidden="true"
            />
            <span className="portfolio-asset-name">{asset.assetName}</span>
            <span
              className={`portfolio-asset-analysis${impact ? ` ${impact.direction.toLowerCase()}` : ''}`}
            >
              {impact
                ? `TOP ${impact.rank} · ${DIRECTION_IMPACT_LABELS[impact.direction]}`
                : '상세 분석 제외'}
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
          <span>{`이 뉴스의 영향 TOP ${impact.rank} / ${analyzedAssetCount}`}</span>
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
          {impact.summary} 현재 보유 비중은 <strong>{`${impact.weight}%`}</strong>예요.
        </p>
        <p>{impact.reason}</p>
      </div>

      <details className="portfolio-evidence">
        <summary>
          판단에 사용한 뉴스 문장
          <span aria-hidden="true" />
        </summary>
        <blockquote>{impact.evidenceSentence}</blockquote>
      </details>
    </article>
  );
}

type PortfolioAnalysisProps = {
  newsId: string;
  assets: PortfolioAsset[];
  portfolioStatus?: 'loading' | 'ready' | 'error';
};

export function PortfolioAnalysis({
  newsId,
  assets,
  portfolioStatus = 'ready',
}: PortfolioAnalysisProps) {
  const [analysis, setAnalysis] = useState<PortfolioAnalysisResponse | null>(null);
  const [analyzedAssets, setAnalyzedAssets] = useState<PortfolioAsset[] | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [selectedAssetName, setSelectedAssetName] = useState<string | null>(null);
  const requestIdRef = useRef(0);

  useEffect(() => {
    requestIdRef.current += 1;
    setAnalysis(null);
    setAnalyzedAssets(null);
    setError(null);
    setSelectedAssetName(null);
    setLoading(false);
  }, [newsId]);

  const analyze = (retry = false) => {
    const snapshot =
      retry && analyzedAssets ? analyzedAssets : assets.map((asset) => ({ ...asset }));
    if (snapshot.length === 0 || loading) return;
    const shouldRefresh = retry || analyzedAssets !== null;

    const requestId = requestIdRef.current + 1;
    requestIdRef.current = requestId;
    setAnalyzedAssets(snapshot);
    setAnalysis(null);
    setError(null);
    setSelectedAssetName(null);
    setLoading(true);

    const request = shouldRefresh
      ? retryPortfolioAnalysis(newsId, snapshot)
      : getPortfolioAnalysis(newsId, snapshot);

    request
      .then((response) => {
        if (requestIdRef.current === requestId) {
          setAnalysis(response);
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

  const handleRetry = () => {
    analyze(true);
  };

  const hasAnalysis = Boolean(analysis && selectedImpact);
  const displayAssets = analyzedAssets ?? assets;
  const hasAnalysisTarget = displayAssets.length > 0;
  const isPortfolioChanged =
    analyzedAssets !== null &&
    createPortfolioSnapshotKey(assets) !== createPortfolioSnapshotKey(analyzedAssets);
  const canAnalyze =
    portfolioStatus === 'ready' &&
    assets.length > 0 &&
    !loading &&
    (analyzedAssets === null || isPortfolioChanged);

  return (
    <section
      className="section portfolio-analysis-section"
      aria-labelledby="portfolio-analysis-title"
    >
      <div className={`portfolio-analysis-heading${hasAnalysis ? ' has-analysis' : ''}`}>
        <div>
          <h2 id="portfolio-analysis-title">
            {hasAnalysis ? '내 포트폴리오에 미치는 영향' : '포트폴리오 분석'}
          </h2>
          <p>
            {analysis && selectedImpact
              ? `보유 종목 ${analysis.totalAssetCount}개 중 이 뉴스의 영향을 크게 받는 상위 ${analysis.analyzedAssetCount}개를 살펴봤어요.`
              : '내 포트폴리오에 미칠 영향을 핵심만 정리했어요.'}
          </p>
        </div>
        {!error ? (
          <button
            className="portfolio-analysis-button"
            type="button"
            disabled={!canAnalyze}
            onClick={() => analyze()}
          >
            {loading ? '분석 중' : analyzedAssets ? '다시 분석하기' : '포트폴리오 분석하기'}
          </button>
        ) : null}
      </div>

      {portfolioStatus === 'loading' ? <PortfolioLoading /> : null}

      {portfolioStatus === 'error' ? <PortfolioLoadError /> : null}

      {portfolioStatus === 'ready' && !hasAnalysisTarget ? <PortfolioEmpty /> : null}

      {portfolioStatus === 'ready' && hasAnalysisTarget && loading ? (
        <PortfolioAnalysisLoading />
      ) : null}

      {portfolioStatus === 'ready' && hasAnalysisTarget && !loading && error ? (
        <PortfolioAnalysisError onRetry={handleRetry} />
      ) : null}

      {portfolioStatus === 'ready' &&
      hasAnalysisTarget &&
      !loading &&
      !error &&
      analysis?.impacts.length === 0 ? (
        <PortfolioAnalysisEmpty />
      ) : null}

      {portfolioStatus === 'ready' &&
      hasAnalysisTarget &&
      !loading &&
      !error &&
      analysis &&
      selectedImpact ? (
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
      ) : null}
    </section>
  );
}
