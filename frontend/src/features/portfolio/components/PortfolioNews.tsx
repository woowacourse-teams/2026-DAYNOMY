import { tourNews } from './portfolioTourExample';
import { trackEvent } from '../../../analytics';
import { trackLoadResult, useContentImpression, useContentView } from '../../news/issue/analytics';
import { useEffect, useRef, useState } from 'react';
import { getStockRelatedContents } from '../../stock-content/api';
import { STOCK_CONTENT_SOURCE_LABELS, type StockRelatedContent } from '../../stock-content/types';
import { feedDate } from '../../news/issue/feed';
import xMark from '../../../assets/platform-x.svg';
import threadsMark from '../../../assets/platform-threads.svg';

type NewsItem = StockRelatedContent & { assets: string[] };

function NewsThumbnail({ item }: { item: NewsItem }) {
  const sourceName = STOCK_CONTENT_SOURCE_LABELS[item.sourceType];

  return (
    <span
      className={`portfolio-news-thumbnail source-${item.sourceType.toLowerCase()}`}
      aria-hidden="true"
    >
      <span className="portfolio-news-thumbnail-fallback">
        {item.sourceType === 'X' || item.sourceType === 'THREADS' ? (
          <img src={item.sourceType === 'X' ? xMark : threadsMark} alt="" />
        ) : item.sourceType === 'YOUTUBE' ? (
          <svg viewBox="0 0 24 24" fill="currentColor">
            <path d="M21.6 7.2a3 3 0 0 0-2.1-2.1C17.6 4.6 12 4.6 12 4.6s-5.6 0-7.5.5a3 3 0 0 0-2.1 2.1C2 9.1 2 12 2 12s0 2.9.4 4.8a3 3 0 0 0 2.1 2.1c1.9.5 7.5.5 7.5.5s5.6 0 7.5-.5a3 3 0 0 0 2.1-2.1c.4-1.9.4-4.8.4-4.8s0-2.9-.4-4.8ZM10 15.5v-7l6 3.5-6 3.5Z" />
          </svg>
        ) : (
          <span className="portfolio-news-thumbnail-source">
            {item.sourceType === 'INTERNAL_NEWS' ? '데이노미' : sourceName}
          </span>
        )}
      </span>
      {item.imageUrl && (
        <img
          src={item.imageUrl}
          alt=""
          loading="lazy"
          onError={(event) => {
            event.currentTarget.hidden = true;
          }}
        />
      )}
    </span>
  );
}

export function PortfolioNews({
  demo = false,
  holdings,
  hidden = false,
  onClose,
}: {
  holdings: { assetId: number; name: string }[];
  demo?: boolean;
  hidden?: boolean;
  onClose?: () => void;
}) {
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
    if (demo) return;
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
                (Date.parse(content.publishedDate ?? '') || 0) >
                (Date.parse(existing.publishedDate ?? '') || 0)
              )
                existing.publishedDate = content.publishedDate;
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
            (a, b) =>
              (Date.parse(b.publishedDate ?? b.createdAt ?? '') || 0) -
                (Date.parse(a.publishedDate ?? a.createdAt ?? '') || 0) ||
              (Date.parse(b.createdAt ?? '') || 0) - (Date.parse(a.createdAt ?? '') || 0),
          ),
        );
        setFailed(results.filter((result) => result.status === 'rejected').length);
        setLoading(false);
      },
    );
    return () => controller.abort();
  }, [assetKey, attempt, demo]);

  const displayItems = demo ? tourNews : items;
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
    settled && !hidden && !demo,
  );
  useContentView(settled && !hidden && !demo && status === 'empty', 'view_content_empty', {
    surface: 'portfolio_news',
    reason: holdings.length ? 'no_linked_content' : 'no_holdings',
  });

  return (
    <aside
      id="portfolio-news-panel"
      className="portfolio-news"
      aria-labelledby="portfolio-news-title"
      hidden={hidden}
    >
      <div ref={panelRef} className="portfolio-news-heading">
        <h2 id="portfolio-news-title">보유 자산 소식</h2>
        <div className="portfolio-news-heading-actions">
          <span>최신순</span>
          {onClose && (
            <button
              type="button"
              className="portfolio-news-close"
              aria-label="소식 접기"
              onClick={onClose}
            >
              <svg aria-hidden="true" viewBox="0 0 24 24">
                <path d="m9 6 6 6-6 6" />
              </svg>
            </button>
          )}
        </div>
      </div>
      <p className="portfolio-news-description">
        {demo
          ? '체험용 가상 소식 · 신제품 수요가 늘었다고 가정해요. 다음 단계에서 내 자산에 미칠 영향을 확인해요.'
          : '내 종목과 연결된 이야기'}
      </p>
      {!demo && loading && (
        <p role="status" className="portfolio-news-state">
          소식을 불러오는 중입니다.
        </p>
      )}
      {!demo && failed > 0 && (
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
      {!demo && !loading && !failed && !items.length && (
        <p className="portfolio-news-state">
          {holdings.length
            ? '보유 자산에 등록된 소식이 없습니다.'
            : '자산을 등록하면 관련 소식이 여기에 모입니다.'}
        </p>
      )}
      <ul className="portfolio-news-list">
        {displayItems.map((item, index) => (
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
              <div className="portfolio-news-copy">
                <NewsThumbnail item={item} />
                <div className="portfolio-news-content">
                  <div className="portfolio-news-meta">
                    <span className="portfolio-news-assets" title={item.assets.join(' · ')}>
                      {item.assets.join(' · ')}
                    </span>
                    <span aria-hidden="true">·</span>
                    <span className="portfolio-news-source">
                      {item.sourceType === 'INTERNAL_NEWS'
                        ? '데이노미'
                        : STOCK_CONTENT_SOURCE_LABELS[item.sourceType]}
                    </span>
                  </div>
                  <strong>
                    {['X 게시물', 'Threads 게시물'].includes(item.title.trim())
                      ? '본문 미리보기가 없는 글입니다. 원문에서 확인하세요.'
                      : item.title.replace(/\[([^\]]+)\]\(https?:\/\/[^)]+\)/g, '$1')}
                  </strong>
                  {Number.isFinite(Date.parse(item.publishedDate ?? item.createdAt ?? '')) && (
                    <time
                      className="portfolio-news-date"
                      dateTime={item.publishedDate ?? item.createdAt ?? undefined}
                    >
                      {feedDate(
                        item.publishedDate ?? item.createdAt,
                        item.publishedDate ? '발행' : '등록',
                      )}
                    </time>
                  )}
                </div>
              </div>
            </a>
          </li>
        ))}
      </ul>
      <a
        onClick={() =>
          trackEvent('click_content_more', {
            surface: 'portfolio_news',
            destination: demo ? 'news_home' : 'portfolio_contents',
          })
        }
        className="portfolio-news-more"
        href={demo ? '/news' : '/news/contents?scope=portfolio'}
      >
        {demo ? '모든 소식 보기' : '보유 종목 소식 더 보기'}
        <svg
          aria-hidden="true"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          strokeWidth="1.7"
          strokeLinecap="round"
          strokeLinejoin="round"
        >
          <path d="M4 12h16m-6-6 6 6-6 6" />
        </svg>
      </a>
    </aside>
  );
}
