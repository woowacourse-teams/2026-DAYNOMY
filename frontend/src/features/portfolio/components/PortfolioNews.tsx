import { trackEvent } from '../../../analytics';
import { trackLoadResult, useContentImpression, useContentView } from '../../news/issue/analytics';
import { useEffect, useRef, useState } from 'react';
import { getStockRelatedContents } from '../../stock-content/api';
import { STOCK_CONTENT_SOURCE_LABELS, type StockRelatedContent } from '../../stock-content/types';

function accountName(item: StockRelatedContent) {
  if (!['X', 'THREADS'].includes(item.sourceType)) return '';
  try {
    const name = new URL(item.url).pathname.split('/').filter(Boolean)[0];
    return name ? `@${name.replace(/^@/, '')}` : '';
  } catch {
    return '';
  }
}

type NewsItem = StockRelatedContent & { assets: string[] };

export function PortfolioNews({ holdings }: { holdings: { assetId: number; name: string }[] }) {
  const assetKey = JSON.stringify(
    [...new Set(holdings.map((holding) => holding.assetId))].sort((a, b) => a - b),
  );
  const panelRef = useRef<HTMLDivElement>(null);
  const [loadedKey, setLoadedKey] = useState<string | null>(null);
  const [items, setItems] = useState<NewsItem[]>([]);
  const [loading, setLoading] = useState(false);
  const [failed, setFailed] = useState(0);
  const [attempt, setAttempt] = useState(0);

  useEffect(() => {
    const startedAt = performance.now();
    setLoadedKey(null);
    const controller = new AbortController();
    const ids: number[] = JSON.parse(assetKey);
    setItems([]);
    setFailed(0);
    if (!ids.length) {
      setLoading(false);
      setLoadedKey(assetKey);
      return () => controller.abort();
    }
    setLoading(true);
    Promise.allSettled(ids.map((id) => getStockRelatedContents(id, controller.signal))).then(
      (results) => {
        if (controller.signal.aborted) return;
        const unique = new Map<string, NewsItem>();
        for (const result of results) {
          if (result.status !== 'fulfilled') continue;
          for (const content of result.value.contents) {
            const existing = unique.get(content.url);
            if (existing) {
              if (
                (Date.parse(content.createdAt ?? '') || 0) >
                (Date.parse(existing.createdAt ?? '') || 0)
              )
                existing.createdAt = content.createdAt;
              if (!existing.assets.includes(result.value.assetName))
                existing.assets.push(result.value.assetName);
            } else unique.set(content.url, { ...content, assets: [result.value.assetName] });
          }
        }
        const failedCount = results.filter((result) => result.status === 'rejected').length;
        trackLoadResult(
          'portfolio_news',
          'all',
          startedAt,
          failedCount === results.length ? 'error' : failedCount ? 'partial_error' : 'success',
          unique.size,
          attempt,
        );
        setLoadedKey(assetKey);
        setItems(
          [...unique.values()].sort(
            (a, b) => (Date.parse(b.createdAt ?? '') || 0) - (Date.parse(a.createdAt ?? '') || 0),
          ),
        );
        setFailed(results.filter((result) => result.status === 'rejected').length);
        setLoading(false);
      },
    );
    return () => controller.abort();
  }, [assetKey, attempt]);

  const settled = !loading && loadedKey === assetKey;
  const status = failed
    ? failed === JSON.parse(assetKey).length
      ? 'error'
      : 'partial_error'
    : items.length
      ? 'ready'
      : 'empty';
  useContentImpression(
    panelRef,
    assetKey,
    'view_portfolio_news',
    { surface: 'portfolio_news', status, result_count: items.length },
    settled,
  );
  useContentView(settled && status === 'empty', 'view_content_empty', {
    surface: 'portfolio_news',
    reason: holdings.length ? 'no_linked_content' : 'no_holdings',
  });

  return (
    <aside className="portfolio-news" aria-labelledby="portfolio-news-title">
      <div ref={panelRef} className="portfolio-news-heading">
        <h2 id="portfolio-news-title">보유 자산 소식</h2>
        <span>최신순</span>
      </div>
      <p className="portfolio-news-description">보유 종목과 연결된 뉴스와 이야기</p>
      {loading && (
        <p role="status" className="portfolio-news-state">
          소식을 불러오는 중입니다.
        </p>
      )}
      {failed > 0 && (
        <p role="alert" className="portfolio-news-state">
          {failed}개 종목의 소식을 불러오지 못했습니다.{' '}
          <button
            aria-label="소식 다시 불러오기"
            onClick={() => {
              trackEvent('retry_content_load', {
                surface: 'portfolio_news',
                area: 'all',
                attempt: attempt + 1,
              });
              setAttempt((value) => value + 1);
            }}
          >
            다시 시도
          </button>
        </p>
      )}
      {!loading && !failed && !items.length && (
        <p className="portfolio-news-state">
          {holdings.length
            ? '보유 자산에 등록된 소식이 없습니다.'
            : '자산을 등록하면 관련 소식이 여기에 모입니다.'}
        </p>
      )}
      <ul className="portfolio-news-list">
        {items.map((item, index) => (
          <li key={item.url}>
            <a
              onClick={() =>
                trackEvent('click_content', {
                  surface: 'portfolio_news',
                  content_id: `content-${item.id}`,
                  content_type: item.sourceType === 'INTERNAL_NEWS' ? 'news' : 'external',
                  source_type: item.sourceType === 'INTERNAL_NEWS' ? 'DAYNOMY' : item.sourceType,
                  position: index + 1,
                })
              }
              href={item.url}
              target={item.sourceType === 'INTERNAL_NEWS' ? undefined : '_blank'}
              rel={item.sourceType === 'INTERNAL_NEWS' ? undefined : 'noreferrer'}
            >
              <div className="portfolio-news-meta">
                <span className={`portfolio-news-source source-${item.sourceType.toLowerCase()}`}>
                  {STOCK_CONTENT_SOURCE_LABELS[item.sourceType]}
                </span>
                {item.createdAt && Number.isFinite(Date.parse(item.createdAt)) && (
                  <time dateTime={item.createdAt}>
                    {new Intl.DateTimeFormat('ko-KR', {
                      month: 'numeric',
                      day: 'numeric',
                      timeZone: 'Asia/Seoul',
                    }).format(new Date(item.createdAt))}{' '}
                    등록
                  </time>
                )}
              </div>
              {accountName(item) && (
                <span className="portfolio-news-account">{accountName(item)}</span>
              )}
              <div className="portfolio-news-copy">
                <strong>
                  {['X 게시물', 'Threads 게시물'].includes(item.title.trim())
                    ? '본문 미리보기가 없는 글입니다. 원문에서 확인하세요.'
                    : item.title.replace(/\[([^\]]+)\]\(https?:\/\/[^)]+\)/g, '$1')}
                </strong>
                {item.imageUrl && <img src={item.imageUrl} alt="" loading="lazy" />}
              </div>
              <div className="portfolio-news-bottom">
                <span className="portfolio-news-assets">{item.assets.join(' · ')}</span>
                <span className="portfolio-news-open">
                  {item.sourceType === 'INTERNAL_NEWS' ? '기사 읽기' : '원문 보기 ↗'}
                </span>
              </div>
            </a>
          </li>
        ))}
      </ul>
      <a
        onClick={() =>
          trackEvent('click_content_more', {
            surface: 'portfolio_news',
            destination: 'news_contents',
          })
        }
        className="portfolio-news-more"
        href="/news/contents"
      >
        전체 콘텐츠 더 보기 <span aria-hidden="true">→</span>
      </a>
    </aside>
  );
}
