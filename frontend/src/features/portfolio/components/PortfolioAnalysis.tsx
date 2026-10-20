import { tourAnalysis } from './portfolioTourExample';
import { trackEvent } from '../../../analytics';
import { useContentImpression } from '../../news/issue/analytics';
import { useMemo, useRef, useState, type ReactNode } from 'react';
import { usePortfolioAnalysis } from '../hooks/usePortfolioAnalysis';
import type {
  PortfolioAsset,
  PortfolioAnalysisSource,
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

function directionClass(direction: PortfolioImpactDirection | null) {
  return direction?.toLowerCase() ?? 'insufficient';
}
function directionLabel(direction: PortfolioImpactDirection | null) {
  return direction === null ? '근거 부족' : DIRECTION_LABELS[direction];
}
function impactLabel(direction: PortfolioImpactDirection | null) {
  return direction === null ? '판단 보류' : DIRECTION_IMPACT_LABELS[direction];
}
function directionColor(direction: PortfolioImpactDirection | null) {
  return direction === null ? '#98a2b3' : DIRECTION_COLORS[direction];
}

const INACTIVE_ASSET_COLOR = '#e8eef8';
const DONUT_CENTER = 110;
const DONUT_RADIUS = 78;
const DONUT_SEGMENT_GAP = 4;

function formatImpactScore(score: number | null) {
  if (score === null) return '판단 보류';
  const sign = score > 0 ? '+' : score < 0 ? '−' : '';
  return `${sign}${Math.abs(score).toFixed(2)}점`;
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

function DirectionIcon({ direction }: { direction: PortfolioImpactDirection | null }) {
  if (direction === null) return null;
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

function ExternalLinkIcon() {
  return (
    <svg aria-hidden="true" viewBox="0 0 20 20">
      <path d="M11 4h5v5M9 11l7-7M16 11v4a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1V5a1 1 0 0 1 1-1h4" />
    </svg>
  );
}

function PortfolioAnalysisPreview({ action }: { action: ReactNode }) {
  const previewImpacts: PortfolioAssetImpactResponse[] = [
    {
      ...tourAnalysis.impacts[0],
      assetName: '예시 자산 A',
      weight: 60,
      direction: 'POSITIVE',
      rank: 1,
    },
    {
      ...tourAnalysis.impacts[0],
      assetName: '예시 자산 B',
      weight: 40,
      direction: 'NEUTRAL',
      rank: 2,
    },
  ];
  return (
    <div className="portfolio-analysis-preview" role="region" aria-label="가상 분석 미리보기">
      <div className="portfolio-analysis-layout portfolio-preview-layout">
        <div className="portfolio-impact-detail">
          <h3>반도체 수요 회복에 따른 긍정적 영향</h3>
          <div className="portfolio-preview-blur" aria-hidden="true">
            <p>
              보유 비중이 높은 종목의 실적 개선 기대가 전체 포트폴리오에 긍정적으로 반영될 수
              있습니다.
            </p>
            <p>원자재 가격과 환율에 따라 종목별 영향은 달라질 수 있습니다.</p>
          </div>
        </div>
        <div className="portfolio-analysis-overview" inert aria-hidden="true">
          <PortfolioDonut
            preview
            assets={previewImpacts}
            impactByAssetName={
              new Map(
                previewImpacts.map((impact) => [normalizeAssetName(impact.assetName), impact]),
              )
            }
            overallDirection={tourAnalysis.overallDirection}
            onSelect={() => {}}
            onSelectOverall={() => {}}
          />
        </div>
      </div>
      <div className="portfolio-preview-action">{action}</div>
    </div>
  );
}

function PortfolioEmpty({ onAddAsset }: { onAddAsset?: () => void }) {
  return (
    <PortfolioAnalysisPreview
      action={
        <button
          type="button"
          className="portfolio-analysis-button"
          onClick={onAddAsset}
          disabled={!onAddAsset}
        >
          자산 추가하기
        </button>
      }
    />
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

function PortfolioAnalysisError({ onRetry, message }: { onRetry: () => void; message: string }) {
  return (
    <div className="portfolio-analysis-state portfolio-analysis-empty" role="alert">
      <strong>{message}</strong>
      <button className="portfolio-analysis-retry-button" type="button" onClick={onRetry}>
        다시 시도
      </button>
    </div>
  );
}

function PortfolioAnalysisTrialExhausted() {
  return (
    <div className="portfolio-analysis-state portfolio-analysis-empty" role="status">
      <strong>무료 분석 체험을 모두 사용했어요.</strong>
      <p>로그인하면 포트폴리오 분석을 계속 이용할 수 있어요.</p>
    </div>
  );
}

type PortfolioDonutProps = {
  preview?: boolean;
  assets: PortfolioAsset[];
  impactByAssetName: Map<string, PortfolioAssetImpactResponse>;
  selectedImpact?: PortfolioAssetImpactResponse;
  overallDirection: PortfolioImpactDirection | null;
  onSelect: (assetName: string) => void;
  onSelectOverall: () => void;
};

function PortfolioDonut({
  preview = false,
  assets,
  impactByAssetName,
  selectedImpact,
  overallDirection,
  onSelect,
  onSelectOverall,
}: PortfolioDonutProps) {
  const orderedAssets = orderAssetsByImpact(assets, impactByAssetName);
  const totalWeight = orderedAssets.reduce((sum, asset) => sum + asset.weight, 0);
  const isOverallSelected = !selectedImpact;
  let offset = 0;

  return (
    <div className="portfolio-analysis-donut-wrap">
      <svg
        className="portfolio-analysis-donut"
        viewBox="0 0 220 220"
        role="group"
        aria-label="전체 포트폴리오의 자산별 보유 비중"
      >
        {isOverallSelected && !preview ? (
          <path
            aria-label={`전체 포트폴리오, ${directionLabel(overallDirection)} 영향`}
            className="portfolio-donut-segment is-overall"
            d={createDonutSegmentPath(0, 100, 38)}
            fill={directionColor(overallDirection)}
            role="img"
          />
        ) : (
          orderedAssets.map((asset) => {
            const impact = impactByAssetName.get(normalizeAssetName(asset.assetName));
            const percentage = totalWeight > 0 ? (asset.weight / totalWeight) * 100 : 0;
            const segmentOffset = offset;
            const isSelected = impact?.assetName === selectedImpact?.assetName;
            const segmentThickness = isSelected ? 41 : 38;
            offset += percentage;

            return (
              <path
                aria-label={
                  impact
                    ? `${asset.assetName}, 보유 비중 ${asset.weight}%, ${directionLabel(impact.direction)} 영향`
                    : `${asset.assetName}, 보유 비중 ${asset.weight}%, 분석 결과 없음`
                }
                aria-pressed={impact ? isSelected : undefined}
                className={`portfolio-donut-segment${impact ? ' is-interactive' : ''}${isSelected ? ' is-selected' : ''}`}
                d={createDonutSegmentPath(segmentOffset, percentage, segmentThickness)}
                data-thickness={segmentThickness}
                fill={
                  impact && (preview || isSelected)
                    ? directionColor(impact.direction)
                    : INACTIVE_ASSET_COLOR
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
          })
        )}
      </svg>

      <button
        className={`portfolio-donut-center ${directionClass(selectedImpact ? selectedImpact.direction : overallDirection)}${isOverallSelected ? ' is-overall' : ''}`}
        type="button"
        aria-label="전체 포트폴리오 분석 보기"
        aria-pressed={isOverallSelected}
        aria-live="polite"
        onClick={onSelectOverall}
      >
        <strong>{selectedImpact?.assetName ?? '전체 포트폴리오'}</strong>
        {selectedImpact ? <b>{`${selectedImpact.weight}%`}</b> : null}
        {isOverallSelected ? <small>전체 분석</small> : null}
      </button>
    </div>
  );
}

type PortfolioAssetListProps = {
  assets: PortfolioAsset[];
  impactByAssetName: Map<string, PortfolioAssetImpactResponse>;
  selectedImpact?: PortfolioAssetImpactResponse;
  overallDirection: PortfolioImpactDirection | null;
  overallScore: number | null;
  onSelect: (assetName: string) => void;
  onSelectOverall: () => void;
};

function PortfolioAssetList({
  assets,
  impactByAssetName,
  selectedImpact,
  overallDirection,
  overallScore,
  onSelect,
  onSelectOverall,
}: PortfolioAssetListProps) {
  const orderedAssets = orderAssetsByImpact(assets, impactByAssetName);

  return (
    <div className="portfolio-asset-selector">
      <div className="portfolio-overall-option">
        <button
          className={`portfolio-asset-row portfolio-overall-row ${directionClass(overallDirection)}${selectedImpact ? '' : ' is-selected'}`}
          type="button"
          aria-label={`전체 포트폴리오 분석 보기, ${directionLabel(overallDirection)} 영향, 영향 점수 ${formatImpactScore(overallScore)}`}
          aria-pressed={!selectedImpact}
          onClick={onSelectOverall}
        >
          <span
            className="portfolio-asset-dot"
            style={{
              backgroundColor: selectedImpact
                ? INACTIVE_ASSET_COLOR
                : directionColor(overallDirection),
            }}
            aria-hidden="true"
          />
          <span className="portfolio-asset-name">전체 포트폴리오</span>
          <span className={`portfolio-asset-analysis ${directionClass(overallDirection)}`}>
            전체 분석
          </span>
          <strong>{formatImpactScore(overallScore)}</strong>
        </button>
      </div>
      <ul className="portfolio-asset-list" aria-label="포트폴리오 보유 자산">
        {orderedAssets.map((asset) => {
          const impact = impactByAssetName.get(normalizeAssetName(asset.assetName));
          const isSelected = impact?.assetName === selectedImpact?.assetName;
          const showDirectionColor = !selectedImpact || isSelected;
          const rowContent = (
            <>
              <span
                className="portfolio-asset-dot"
                style={{
                  backgroundColor:
                    showDirectionColor && impact
                      ? directionColor(impact.direction)
                      : INACTIVE_ASSET_COLOR,
                }}
                aria-hidden="true"
              />
              <span className="portfolio-asset-name">{asset.assetName}</span>
              <span
                className={`portfolio-asset-analysis${impact ? ` ${directionClass(impact.direction)}` : ''}`}
              >
                {impact?.direction ? `TOP ${impact.rank}` : '근거 부족'}
              </span>
            </>
          );

          return (
            <li key={asset.assetName}>
              {impact ? (
                <button
                  className={`portfolio-asset-row ${directionClass(impact.direction)}${isSelected ? ' is-selected' : ''}`}
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
    </div>
  );
}

// 링크와 인용 표시는 출처 영역에서 제공하고 본문은 텍스트로만 읽게 한다.
function plainAnalysisText(text: string) {
  return text
    .replace(/\[([^\]]+)\]\(https?:\/\/[^)]+\)/g, '$1')
    .replace(/https?:\/\/[^\s<>]+/g, '')
    .replace(/\[\d+(?:\s*[,–-]\s*\d+)*\]/g, '')
    .replace(/【[^】]*】/g, '')
    .replace(/\*\*/g, '')
    .trim();
}

function AnalysisParagraph({ text }: { text: string }) {
  const copy = plainAnalysisText(text);
  if (!copy) return null;
  return <p>{copy}</p>;
}

function AnalysisSources({
  sources,
  scope,
  demo = false,
}: {
  sources: PortfolioAnalysisSource[];
  scope: 'overall' | 'asset';
  demo?: boolean;
}) {
  return (
    <details
      className="portfolio-evidence"
      open={demo ? true : undefined}
      onToggle={(event) => {
        if (!demo && event.currentTarget.open)
          trackEvent('expand_portfolio_analysis_sources', {
            surface: 'portfolio_analysis',
            scope,
            source_count: sources.length,
          });
      }}
    >
      <summary>
        <span className="portfolio-evidence-label is-closed">분석 출처 모두 보기</span>
        <span className="portfolio-evidence-label is-open">분석 출처 접기</span>
        <span className="portfolio-evidence-toggle-icon" aria-hidden="true" />
      </summary>
      {sources.length ? (
        <ul className="portfolio-evidence-sources">
          {sources.map((source) => (
            <li key={source.url}>
              <a href={source.url} target="_blank" rel="noopener noreferrer">
                {source.title}
                <ExternalLinkIcon />
              </a>
              <div className="portfolio-source-dates">
                {source.publishedOn ? (
                  <span>
                    발행일 <time dateTime={source.publishedOn}>{source.publishedOn}</time>
                  </span>
                ) : (
                  <span>발행일 미확인</span>
                )}
                {source.occurredOn && source.occurredOn !== source.publishedOn ? (
                  <span>
                    사건일 <time dateTime={source.occurredOn}>{source.occurredOn}</time>
                  </span>
                ) : null}
              </div>
              {source.excerpt ? <p>{source.excerpt}</p> : null}
            </li>
          ))}
        </ul>
      ) : (
        <p>연결된 출처가 없어요.</p>
      )}
    </details>
  );
}

function PortfolioOverallImpactDetail({
  analysis,
  analyzedAtLabel,
  demo,
}: {
  analysis: PortfolioAnalysisResponse;
  analyzedAtLabel: string | null;
  demo?: boolean;
}) {
  return (
    <article
      className={`portfolio-impact-detail is-overall ${directionClass(analysis.overallDirection)}`}
    >
      <div className="portfolio-detail-header">
        <div>
          <span>전체 포트폴리오 예상 영향</span>
          <h3>전체 포트폴리오</h3>
        </div>
        <div className="portfolio-detail-badge-group">
          <span className="portfolio-direction-badge">
            <DirectionIcon direction={analysis.overallDirection} />
            {impactLabel(analysis.overallDirection)}
          </span>
          <small>{`영향 점수 ${formatImpactScore(analysis.overallScore)}`}</small>
        </div>
      </div>

      <div className="portfolio-detail-copy">
        {analysis.overallImpact
          .split(/\n\s*\n/)
          .filter(Boolean)
          .map((paragraph, index) => (
            <AnalysisParagraph key={index} text={paragraph} />
          ))}
      </div>

      {analyzedAtLabel ? (
        <time className="portfolio-detail-time" dateTime={analysis.analyzedAt ?? undefined}>
          {analyzedAtLabel}
        </time>
      ) : null}

      <AnalysisSources demo={demo} scope="overall" sources={analysis.sources} />
    </article>
  );
}

function PortfolioImpactDetail({
  impact,
  analyzedAssetCount,
  demo,
}: {
  impact: PortfolioAssetImpactResponse;
  analyzedAssetCount: number;
  demo?: boolean;
}) {
  return (
    <article className={`portfolio-impact-detail ${directionClass(impact.direction)}`}>
      <div className="portfolio-detail-header">
        <div>
          <span>
            {impact.direction === null
              ? '보유 종목 · 근거 부족'
              : `포트폴리오 영향 TOP ${impact.rank} / ${analyzedAssetCount}`}
          </span>
          <h3>{impact.assetName}</h3>
        </div>
        <div className="portfolio-detail-badge-group">
          <span className="portfolio-direction-badge">
            <DirectionIcon direction={impact.direction} />
            {impactLabel(impact.direction)}
          </span>
          <small>{`영향 수준 ${impact.impactLevel ? IMPACT_LEVEL_LABELS[impact.impactLevel] : '판단 보류'}`}</small>
        </div>
      </div>

      <div className="portfolio-detail-copy">
        {impact.direction === null ? (
          <p>판단할 근거가 충분하지 않아요.</p>
        ) : (
          <>
            <AnalysisParagraph text={impact.issueSummary} />
            <AnalysisParagraph text={impact.expectedReaction} />
            <AnalysisParagraph text={impact.outlook} />
          </>
        )}
      </div>

      <AnalysisSources demo={demo} scope="asset" sources={impact.sources} />
    </article>
  );
}

type PortfolioAnalysisProps = {
  demoStep?: number;
  assets: PortfolioAsset[];
  isLoggedIn?: boolean;
  memberId?: number;
  holdingsKey?: string;
  onAddAsset?: () => void;
};

export function PortfolioAnalysis(props: PortfolioAnalysisProps) {
  if (props.demoStep !== undefined)
    return (
      <PortfolioAnalysisView
        {...props}
        state={{
          analysis: props.demoStep >= 12 ? tourAnalysis : null,
          saved: null,
          loading: false,
          loaded: true,
          error: null,
          trialExhausted: false,
          portfolioChanged: false,
          analyze: async () => {},
        }}
      />
    );
  return (
    <PortfolioAnalysisContent
      key={`${props.isLoggedIn === false ? 'guest' : `member:${props.memberId ?? 'current'}`}:${props.holdingsKey ?? ''}`}
      {...props}
    />
  );
}

function PortfolioAnalysisContent(props: PortfolioAnalysisProps) {
  const state = usePortfolioAnalysis(props.assets, props.isLoggedIn ?? true, props.holdingsKey);
  return <PortfolioAnalysisView {...props} state={state} />;
}
function PortfolioAnalysisView({
  assets,
  isLoggedIn = true,
  demoStep,
  onAddAsset,
  state,
}: PortfolioAnalysisProps & { state: ReturnType<typeof usePortfolioAnalysis> }) {
  const { analysis, saved, loading, loaded, error, trialExhausted, portfolioChanged, analyze } =
    state;
  const [selectedAssetName, setSelectedAssetName] = useState<string | null>(null);
  const sortedImpacts = useMemo(
    () => [...(analysis?.impacts ?? [])].sort((left, right) => left.rank - right.rank),
    [analysis],
  );
  const impactByAssetName = useMemo(
    () => new Map(sortedImpacts.map((impact) => [normalizeAssetName(impact.assetName), impact])),
    [sortedImpacts],
  );
  const selectedImpact =
    demoStep !== undefined && demoStep >= 14
      ? sortedImpacts[0]
      : selectedAssetName
        ? sortedImpacts.find((impact) => impact.assetName === selectedAssetName)
        : undefined;
  const analyzedAtLabel = formatAnalyzedAt(analysis?.analyzedAt ?? null);

  const handleRetry = () => {
    analyze();
  };

  const hasAnalysis = Boolean(analysis && sortedImpacts.length > 0);
  const resultRef = useRef<HTMLDivElement>(null);
  useContentImpression(
    resultRef,
    String(saved?.analysisId ?? analysis?.analyzedAt ?? 'result'),
    'view_portfolio_analysis',
    {
      surface: 'portfolio_analysis',
      user_type: isLoggedIn ? 'member' : 'guest',
      analyzed_asset_count: analysis?.analyzedAssetCount,
    },
    hasAnalysis && demoStep === undefined,
  );
  const selectAsset = (assetName: string | null, surface: string) => {
    if (demoStep !== undefined || assetName === selectedAssetName) return;
    setSelectedAssetName(assetName);
    trackEvent('select_portfolio_analysis_asset', {
      surface,
      scope: assetName === null ? 'overall' : 'asset',
      position:
        assetName === null
          ? undefined
          : sortedImpacts.find((impact) => impact.assetName === assetName)?.rank,
    });
  };
  const displayAssets = analysis
    ? analysis.impacts.map(({ assetName, weight }) => ({ assetName, weight }))
    : assets;
  const hasAnalysisTarget = displayAssets.length > 0;
  const canAnalyze =
    !loading && (assets.length > 0 || (isLoggedIn && (!loaded || saved?.status === 'PROCESSING')));

  const showIntroduction =
    hasAnalysisTarget &&
    !analysis &&
    !loading &&
    !error &&
    !trialExhausted &&
    saved?.status !== 'PROCESSING';
  const analysisAction = !hasAnalysisTarget ? null : !isLoggedIn && trialExhausted ? (
    <a className="portfolio-analysis-button" href="/login?returnTo=%2F">
      로그인하고 다시 분석하기
    </a>
  ) : !error ? (
    <button
      className={`portfolio-analysis-button${!isLoggedIn && !analysis && !loading && !portfolioChanged && saved?.status !== 'PROCESSING' ? ' is-trial' : ''}`}
      type="button"
      disabled={!canAnalyze}
      onClick={analyze}
    >
      {loading
        ? analysis
          ? '확인 중'
          : '분석 중'
        : saved?.status === 'PROCESSING'
          ? '진행 상태 확인'
          : portfolioChanged
            ? '현재 자산으로 분석하기'
            : analysis
              ? '새 소식 확인'
              : isLoggedIn
                ? 'AI 분석하기'
                : '1회 분석 체험하기'}
    </button>
  ) : null;

  return (
    <section
      data-tour-state={
        loading ? 'loading' : error ? 'error' : trialExhausted && !analysis ? 'exhausted' : 'ready'
      }
      className="portfolio-analysis-section"
      aria-labelledby="portfolio-ai-analysis-title"
    >
      <div
        data-portfolio-tour={showIntroduction ? undefined : 'impact'}
        className={`portfolio-analysis-heading${hasAnalysis ? ' has-analysis' : ''}`}
      >
        <div>
          <h2 id="portfolio-ai-analysis-title">포트폴리오 AI 분석</h2>
          {analysis ? (
            <p>
              보유 자산 {analysis.totalAssetCount}개 중 {analysis.analyzedAssetCount}개 분석
            </p>
          ) : null}
        </div>
        {!showIntroduction ? analysisAction : null}
      </div>

      <div className="portfolio-analysis-meta">
        {portfolioChanged ? (
          <p role="status">
            자산 구성이 바뀌어 이전 분석을 숨겼어요. 현재 보유 자산으로 다시 분석해 주세요.
          </p>
        ) : null}

        {analysis && loading ? <p role="status">기존 분석을 유지하며 확인하고 있어요.</p> : null}
        {analysis && !loading && saved?.status === 'RECHECK_REQUIRED' ? (
          <p role="status">기존 분석을 유지하고 있어요. 근거를 다시 확인해 주세요.</p>
        ) : null}
        {analysis &&
        !loading &&
        saved?.status === 'READY' &&
        saved.lastCheckOutcome === 'UNCHANGED' ? (
          <p role="status">주요 변화가 없어 기존 분석을 유지했어요.</p>
        ) : null}
        {analysis &&
        !loading &&
        saved?.status === 'READY' &&
        saved.lastCheckOutcome === 'UPDATED' &&
        saved.changeReason ? (
          <p role="status">{saved.changeReason}</p>
        ) : null}
        {analysis && saved?.lastSucceededAt ? (
          <p className="portfolio-analysis-checked-at">
            최근 확인:{' '}
            <time dateTime={saved.lastSucceededAt}>
              {formatAnalyzedAt(saved.lastSucceededAt)?.replace(' 분석 기준', '')}
            </time>
          </p>
        ) : null}
      </div>
      {!loading && saved?.status === 'PROCESSING' ? (
        <div
          className="portfolio-analysis-state portfolio-analysis-empty"
          role="status"
          aria-live="polite"
        >
          <span className="portfolio-analysis-spinner" aria-hidden="true" />
          <p>서버에서 분석 중이에요. 완료되면 결과를 자동으로 표시해요.</p>
        </div>
      ) : null}
      {!hasAnalysisTarget ? <PortfolioEmpty onAddAsset={onAddAsset} /> : null}

      {showIntroduction ? (
        <div data-portfolio-tour="impact">
          <PortfolioAnalysisPreview action={analysisAction} />
        </div>
      ) : null}

      {hasAnalysisTarget && loading && !hasAnalysis ? <PortfolioAnalysisLoading /> : null}

      {hasAnalysisTarget && !loading && error ? (
        <PortfolioAnalysisError onRetry={handleRetry} message={error} />
      ) : null}

      {hasAnalysisTarget && !loading && !error && trialExhausted && !analysis ? (
        <PortfolioAnalysisTrialExhausted />
      ) : null}

      {hasAnalysisTarget && !loading && !error && analysis?.impacts.length === 0 ? (
        <PortfolioAnalysisEmpty />
      ) : null}

      {hasAnalysisTarget && analysis && sortedImpacts.length > 0 ? (
        <>
          <div ref={resultRef} className="portfolio-analysis-layout">
            {selectedImpact ? (
              <PortfolioImpactDetail
                demo={demoStep !== undefined}
                impact={selectedImpact}
                analyzedAssetCount={analysis.analyzedAssetCount}
              />
            ) : (
              <PortfolioOverallImpactDetail
                demo={demoStep !== undefined}
                analysis={analysis}
                analyzedAtLabel={analyzedAtLabel}
              />
            )}
            <div className="portfolio-analysis-overview">
              <div className="portfolio-analysis-overview-heading">
                <h3>자산별 영향</h3>
              </div>

              <PortfolioDonut
                assets={displayAssets}
                impactByAssetName={impactByAssetName}
                selectedImpact={selectedImpact}
                overallDirection={analysis.overallDirection}
                onSelect={(name) => selectAsset(name, 'portfolio_analysis_chart')}
                onSelectOverall={() => selectAsset(null, 'portfolio_analysis_chart')}
              />
              <PortfolioAssetList
                assets={displayAssets}
                impactByAssetName={impactByAssetName}
                selectedImpact={selectedImpact}
                overallDirection={analysis.overallDirection}
                overallScore={analysis.overallScore}
                onSelect={(name) => selectAsset(name, 'portfolio_analysis_list')}
                onSelectOverall={() => selectAsset(null, 'portfolio_analysis_list')}
              />
              <p className="portfolio-weight-caption">
                {selectedImpact
                  ? '분석 당시 전체 포트폴리오에서 차지한 비중이에요.'
                  : '색상은 각 자산에 예상되는 영향 방향을 나타내요.'}
              </p>
            </div>
          </div>
          <div className="portfolio-score-panel">
            <div className="portfolio-score-guide">
              <h3>영향 점수 산정 기준</h3>
              <p>
                보유 비중과 자산별 영향 방향·수준을 합산한 -100~100 지표예요. 예상 수익률이나 상승
                확률은 아니에요.
              </p>
              <p>
                최근 자료를 우선 확인하고, 여전히 유효한 과거 발표는 날짜를 밝혀 배경 근거로
                활용해요. 근거가 부족한 종목은 중립으로 간주하지 않고 영향 점수 산정에서 제외해요.
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
            </div>
          </div>
        </>
      ) : null}
    </section>
  );
}
