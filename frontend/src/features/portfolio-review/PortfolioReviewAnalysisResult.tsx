import { useId, useState } from 'react';
import type { PortfolioAnalysisResponse, PortfolioImpactDirection } from '../portfolio/types';
import '../portfolio/portfolioAnalysis.css';
import './portfolioReviewAnalysisResult.css';

const labels = { POSITIVE: '긍정 영향', NEGATIVE: '부정 영향', NEUTRAL: '중립 영향' };
const colors = { POSITIVE: '#ef464d', NEGATIVE: '#3e83d7', NEUTRAL: '#98a2b3' };
const levels = { HIGH: '높음', MEDIUM: '보통', LOW: '낮음' };
const tone = (direction: PortfolioImpactDirection | null) =>
  direction?.toLowerCase() ?? 'insufficient';
const label = (direction: PortfolioImpactDirection | null) =>
  direction ? labels[direction] : '판단 보류';
const color = (direction: PortfolioImpactDirection | null) =>
  direction ? colors[direction] : '#98a2b3';
const score = (value: number | null) =>
  value === null
    ? '판단 보류'
    : `${value > 0 ? '+' : value < 0 ? '−' : ''}${Math.abs(value).toFixed(2)}점`;

function Paragraph({ text }: { text: string }) {
  const plain = text
    .replace(/\[([^\]]+)\]\(https?:\/\/[^)]+\)/g, '$1')
    .replace(/https?:\/\/[^\s<>]+/g, '')
    .replace(/\[\d+(?:\s*[,–-]\s*\d+)*\]/g, '')
    .replace(/【[^】]*】/g, '')
    .replace(/\*\*/g, '')
    .trim();
  return plain ? <p>{plain}</p> : null;
}

export function PortfolioReviewAnalysisResult({
  analysis,
  title,
  showOverall = true,
  fullLayout = false,
}: {
  analysis: PortfolioAnalysisResponse;
  title: string;
  showOverall?: boolean;
  fullLayout?: boolean;
}) {
  const id = useId();
  const [selected, setSelected] = useState<string | null>(null);
  const impacts = [...analysis.impacts].sort((a, b) => a.rank - b.rank);
  const selectedImpact =
    impacts.find((impact) => impact.assetName === selected) ??
    (showOverall ? undefined : impacts[0]);
  const assets = [...impacts, ...(showOverall ? (analysis.excludedAssets ?? []) : [])];
  const total = assets.reduce((sum, asset) => sum + asset.weight, 0);
  const direction =
    selectedImpact?.direction ??
    (selectedImpact ? null : showOverall ? analysis.overallDirection : null);
  const sources = [
    ...new Map(
      (selectedImpact ? selectedImpact.sources : showOverall ? analysis.sources : []).map(
        (source) => [source.url, source],
      ),
    ).values(),
  ];
  let offset = 0;
  const overall = showOverall && !selectedImpact;

  return (
    <section
      className={`portfolio-review-result portfolio-analysis-section ${fullLayout ? 'is-full-layout' : 'is-compact-layout'}`}
      aria-label={title}
    >
      <header className="portfolio-analysis-heading has-analysis">
        <h2>{title}</h2>
      </header>
      {!showOverall ? (
        <p className="portfolio-review-result-note">
          일부 종목만 공개되어 전체 포트폴리오 점수와 종합 요약은 숨겼어요.
        </p>
      ) : null}
      <div className="portfolio-analysis-layout">
        <div className="portfolio-analysis-overview">
          <div className="portfolio-analysis-overview-heading">
            <h3>자산별 영향</h3>
          </div>
          <div className="portfolio-analysis-donut-wrap">
            <svg
              className="portfolio-analysis-donut"
              viewBox="0 0 220 220"
              role="group"
              aria-label={
                showOverall ? '전체 포트폴리오의 자산별 보유 비중' : '공개 종목 내 자산별 비중'
              }
            >
              {overall ? (
                <circle
                  cx="110"
                  cy="110"
                  r="78"
                  fill="none"
                  stroke={color(direction)}
                  strokeWidth="38"
                  role="img"
                  aria-label={`전체 포트폴리오, ${label(direction)}`}
                />
              ) : (
                assets.map((asset) => {
                  const impact = impacts.find((item) => item.assetName === asset.assetName);
                  const percentage = total > 0 ? (asset.weight / total) * 100 : 0;
                  const start = offset;
                  offset += percentage;
                  const active = asset.assetName === selectedImpact?.assetName;
                  return (
                    <path
                      key={asset.assetName}
                      d={segmentPath(start, percentage, active ? 41 : 38)}
                      className={`portfolio-donut-segment${impact ? ' is-interactive' : ''}${active ? ' is-selected' : ''}`}
                      fill={active && impact ? color(impact.direction) : '#e8eef8'}
                      role={impact ? 'button' : 'img'}
                      tabIndex={impact ? 0 : undefined}
                      aria-label={`${asset.assetName}, 보유 비중 ${asset.weight}%${impact ? `, ${label(impact.direction)}` : ', 분석 대상 제외'}`}
                      aria-pressed={impact ? active : undefined}
                      onClick={impact ? () => setSelected(asset.assetName) : undefined}
                      onKeyDown={
                        impact
                          ? (event) => {
                              if (event.key === 'Enter' || event.key === ' ') {
                                event.preventDefault();
                                setSelected(asset.assetName);
                              }
                            }
                          : undefined
                      }
                    />
                  );
                })
              )}
            </svg>
            {showOverall ? (
              <button
                type="button"
                className={`portfolio-donut-center ${tone(direction)}${overall ? ' is-overall' : ''}`}
                aria-label="전체 포트폴리오 분석 보기"
                aria-pressed={overall}
                onClick={() => setSelected(null)}
              >
                <strong>{selectedImpact?.assetName ?? '전체 포트폴리오'}</strong>
                {selectedImpact ? <b>{selectedImpact.weight}%</b> : <small>전체 분석</small>}
              </button>
            ) : (
              <div className={`portfolio-donut-center ${tone(direction)}`}>
                <strong>{selectedImpact?.assetName ?? '공개 종목'}</strong>
                {selectedImpact ? <b>{selectedImpact.weight}%</b> : null}
              </div>
            )}
          </div>
          <div className="portfolio-asset-selector">
            {showOverall ? (
              <div className="portfolio-overall-option">
                <button
                  type="button"
                  className={`portfolio-asset-row portfolio-overall-row ${tone(analysis.overallDirection)}${overall ? ' is-selected' : ''}`}
                  aria-pressed={overall}
                  onClick={() => setSelected(null)}
                >
                  <span
                    className="portfolio-asset-dot"
                    style={{
                      backgroundColor: overall ? color(analysis.overallDirection) : '#e8eef8',
                    }}
                    aria-hidden="true"
                  />
                  <span className="portfolio-asset-name">전체 포트폴리오</span>
                  <span className="portfolio-asset-analysis">전체 분석</span>
                  <strong>{score(analysis.overallScore)}</strong>
                </button>
              </div>
            ) : null}
            <ul className="portfolio-asset-list" aria-label="포트폴리오 보유 자산">
              {assets.map((asset) => {
                const impact = impacts.find((item) => item.assetName === asset.assetName);
                const active = selectedImpact?.assetName === asset.assetName;
                return (
                  <li key={asset.assetName}>
                    <button
                      type="button"
                      disabled={!impact}
                      className={`portfolio-asset-row ${impact ? tone(impact.direction) : 'is-disabled'}${active ? ' is-selected' : ''}`}
                      aria-pressed={active}
                      onClick={() => setSelected(asset.assetName)}
                    >
                      <span
                        className="portfolio-asset-dot"
                        style={{
                          backgroundColor:
                            impact && (overall || active) ? color(impact.direction) : '#e8eef8',
                        }}
                        aria-hidden="true"
                      />
                      <span className="portfolio-asset-name">{asset.assetName}</span>
                      <span
                        className={`portfolio-asset-analysis ${impact ? tone(impact.direction) : ''}`}
                      >
                        {impact
                          ? impact.direction
                            ? `TOP ${impact.rank}`
                            : '근거 부족'
                          : '분석 대상 제외'}
                      </span>
                    </button>
                  </li>
                );
              })}
            </ul>
          </div>
          <p className="portfolio-weight-caption">
            {!showOverall
              ? '차트는 공개 종목끼리의 비율이며, 표시 비중은 전체 포트폴리오 기준이에요.'
              : selectedImpact
                ? '분석 당시 전체 포트폴리오에서 차지한 비중이에요.'
                : '색상은 각 자산에 예상되는 영향 방향을 나타내요.'}
          </p>
        </div>
        <article
          className={`portfolio-impact-detail ${overall ? 'is-overall ' : ''}${tone(direction)}`}
          aria-labelledby={`${id}-detail`}
        >
          <div className="portfolio-detail-header">
            <div>
              <span>
                {selectedImpact
                  ? selectedImpact.direction
                    ? `포트폴리오 영향 TOP ${selectedImpact.rank} / ${analysis.analyzedAssetCount}`
                    : '보유 종목 · 근거 부족'
                  : '전체 포트폴리오 예상 영향'}
              </span>
              <h3 id={`${id}-detail`} aria-live="polite">
                {selectedImpact?.assetName ?? (showOverall ? '전체 포트폴리오' : '공개 종목 분석')}
              </h3>
            </div>
            <div className="portfolio-detail-badge-group">
              <span className="portfolio-direction-badge">
                {direction ? (
                  <svg
                    aria-hidden="true"
                    viewBox="0 0 20 20"
                    className={direction === 'NEGATIVE' ? 'is-negative' : undefined}
                  >
                    <path
                      d={
                        direction === 'NEUTRAL' ? 'M3 10h13m-4-4 4 4-4 4' : 'M10 17V3m-5 5 5-5 5 5'
                      }
                    />
                  </svg>
                ) : null}
                {label(direction)}
              </span>
              <small>
                {selectedImpact
                  ? `영향 수준 ${selectedImpact.impactLevel ? levels[selectedImpact.impactLevel] : '판단 보류'}`
                  : showOverall
                    ? `영향 점수 ${score(analysis.overallScore)}`
                    : ''}
              </small>
            </div>
          </div>
          <div className="portfolio-detail-copy">
            {selectedImpact ? (
              selectedImpact.direction === null ? (
                <p>판단할 근거가 충분하지 않아요.</p>
              ) : (
                <>
                  <Paragraph text={selectedImpact.issueSummary} />
                  <Paragraph text={selectedImpact.expectedReaction} />
                  <Paragraph text={selectedImpact.outlook} />
                </>
              )
            ) : showOverall ? (
              (analysis.overallImpact || '자산별 분석 결과를 확인해 주세요.')
                .split(/\n\s*\n/)
                .filter(Boolean)
                .map((text, index) => <Paragraph key={index} text={text} />)
            ) : (
              <p>표시할 공개 종목 분석이 없어요.</p>
            )}
          </div>
          {overall && analysis.analyzedAt ? (
            <time className="portfolio-detail-time" dateTime={analysis.analyzedAt}>
              {new Date(analysis.analyzedAt).toLocaleString('ko-KR')} 분석 기준
            </time>
          ) : null}
          <section className="portfolio-evidence" aria-labelledby={`${id}-sources`}>
            <h4 id={`${id}-sources`}>분석 출처</h4>
            {sources.length ? (
              <ul className="portfolio-evidence-sources">
                {sources.map((source) => (
                  <li key={source.url}>
                    <a
                      href={source.url}
                      target="_blank"
                      rel="noreferrer"
                      referrerPolicy="no-referrer"
                    >
                      {source.title}
                      <svg aria-hidden="true" viewBox="0 0 20 20">
                        <path d="M11 4h5v5M9 11l7-7M16 11v4a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1V5a1 1 0 0 1 1-1h4" />
                      </svg>
                    </a>
                    <div className="portfolio-source-dates">
                      <span>
                        {source.publishedOn ? (
                          <>
                            발행일 <time dateTime={source.publishedOn}>{source.publishedOn}</time>
                          </>
                        ) : (
                          '발행일 미확인'
                        )}
                      </span>
                      {source.occurredOn && source.occurredOn !== source.publishedOn ? (
                        <span>
                          사건일 <time dateTime={source.occurredOn}>{source.occurredOn}</time>
                        </span>
                      ) : null}
                    </div>
                  </li>
                ))}
              </ul>
            ) : (
              <p>연결된 출처가 없어요.</p>
            )}
          </section>
        </article>
      </div>
      {showOverall ? (
        <div className="portfolio-score-panel">
          <section className="portfolio-score-guide" aria-labelledby={`${id}-score-guide`}>
            <h3 id={`${id}-score-guide`}>영향 점수 산정 기준</h3>
            <p>
              보유 비중과 자산별 영향 방향·수준을 합산한 -100~100 지표예요. 예상 수익률이나 상승
              확률은 아니에요.
            </p>
            <p>근거가 부족한 종목은 중립으로 간주하지 않고 영향 점수 산정에서 제외해요.</p>
            <dl>
              <div>
                <dt>긍정 기여</dt>
                <dd>{score(analysis.positiveImpactScore)}</dd>
              </div>
              <div>
                <dt>부정 기여</dt>
                <dd>−{Math.abs(analysis.negativeImpactScore).toFixed(2)}점</dd>
              </div>
              <div>
                <dt>전체 영향</dt>
                <dd>{score(analysis.overallScore)}</dd>
              </div>
            </dl>
          </section>
        </div>
      ) : null}
    </section>
  );
}

// 원본 파일을 변경하지 않고 같은 도넛 형태를 유지하기 위한 표시 전용 기하 계산이다.
function segmentPath(start: number, percentage: number, thickness: number) {
  const outer = 78 + thickness / 2;
  const inner = 78 - thickness / 2;
  const point = (percent: number, radius: number) => {
    const angle = (percent / 100) * Math.PI * 2 - Math.PI / 2;
    return `${110 + radius * Math.cos(angle)} ${110 + radius * Math.sin(angle)}`;
  };
  if (percentage <= 0) return '';
  if (percentage >= 99.999)
    return `M ${point(0, outer)} A ${outer} ${outer} 0 1 1 ${point(50, outer)} A ${outer} ${outer} 0 1 1 ${point(0, outer)} L ${point(0, inner)} A ${inner} ${inner} 0 1 0 ${point(50, inner)} A ${inner} ${inner} 0 1 0 ${point(0, inner)} Z`;
  const inset = (radius: number) =>
    Math.min((Math.asin(Math.min(2 / radius, 1)) / (Math.PI * 2)) * 100, percentage / 2);
  const end = start + percentage;
  const arc = percentage > 50 ? 1 : 0;
  return `M ${point(start + inset(outer), outer)} A ${outer} ${outer} 0 ${arc} 1 ${point(end - inset(outer), outer)} L ${point(end - inset(inner), inner)} A ${inner} ${inner} 0 ${arc} 0 ${point(start + inset(inner), inner)} Z`;
}
