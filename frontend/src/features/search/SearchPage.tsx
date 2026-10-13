import { useEffect, useRef, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { trackEvent } from '../../analytics';
import { IssueFeed } from '../news/issue/IssueFeed';
import { IssuePagination } from '../news/issue/IssuePagination';
import type { FeedItem } from '../news/issue/feed';
import { searchContents } from './api';
import { isContentSearchSource, type ContentSearchSource } from './response';
import '../news/issue/issue.css';

const PAGE_SIZE = 12;

function getSource(params: URLSearchParams): ContentSearchSource {
  const source = params.get('source');
  return isContentSearchSource(source) ? source : 'ALL';
}

function getPage(params: URLSearchParams) {
  const page = Number(params.get('page'));
  return Number.isSafeInteger(page) && page > 0 ? page : 1;
}

function SearchPage() {
  const [params, setParams] = useSearchParams();
  const keyword = params.get('q')?.trim() ?? '';
  const source = getSource(params);
  const page = getPage(params);
  const [items, setItems] = useState<FeedItem[]>([]);
  const [totalPages, setTotalPages] = useState(0);
  const [loading, setLoading] = useState(Boolean(keyword));
  const [error, setError] = useState<string | null>(null);
  const [attempt, setAttempt] = useState(0);
  const titleRef = useRef<HTMLHeadingElement>(null);

  useEffect(() => {
    if (keyword) titleRef.current?.focus();
  }, [keyword]);

  useEffect(() => {
    if (!keyword) return;
    let ignore = false;
    setLoading(true);
    setError(null);
    searchContents(keyword, source, page, PAGE_SIZE)
      .then((result) => {
        if (ignore) return;
        if (result.totalPages > 0 && page > result.totalPages) {
          setParams({ q: keyword, source, page: String(result.totalPages) }, { replace: true });
          return;
        }
        setItems(result.items);
        setTotalPages(result.totalPages);
      })
      .catch((caught: unknown) => {
        if (ignore) return;
        setItems([]);
        setTotalPages(0);
        setError(caught instanceof Error ? caught.message : '검색 결과를 불러오지 못했습니다.');
      })
      .finally(() => {
        if (!ignore) setLoading(false);
      });
    return () => {
      ignore = true;
    };
  }, [keyword, source, page, attempt, setParams]);

  useEffect(() => {
    if (keyword) trackEvent('search_news', { search_length: keyword.length });
  }, [keyword]);

  function changeSource(nextSource: ContentSearchSource) {
    setParams({ q: keyword, source: nextSource, page: '1' });
  }

  function changePage(nextPage: number) {
    setParams({ q: keyword, source, page: String(nextPage) });
    document.getElementById('search-results')?.scrollIntoView({ behavior: 'smooth' });
  }

  return (
    <main className="issue-page search-page">
      <header className="issue-heading">
        <h1 ref={titleRef} tabIndex={-1}>
          {keyword ? `‘${keyword}’ 검색 결과` : '소식 검색'}
        </h1>
        <p>데이노미, Threads, YouTube, X의 소식을 찾아보세요.</p>
      </header>
      {keyword && (
        <section id="search-results" aria-labelledby="search-results-heading">
          <div className="issue-section-heading">
            <h2 id="search-results-heading">검색된 소식</h2>
            <div className="issue-feed-controls">
              <label>
                <span className="sr-only">소식 출처</span>
                <select
                  value={source}
                  onChange={(event) => changeSource(event.target.value as ContentSearchSource)}
                >
                  <option value="ALL">모든 출처</option>
                  <option value="DAYNOMY">데이노미</option>
                  <option value="X">X</option>
                  <option value="THREADS">Threads</option>
                  <option value="YOUTUBE">YouTube</option>
                </select>
              </label>
              <span>최신순</span>
            </div>
          </div>
          {loading && (
            <p className="issue-state" role="status">
              검색 중입니다.
            </p>
          )}
          {!loading && error && (
            <p className="issue-state" role="alert">
              {error}{' '}
              <button type="button" onClick={() => setAttempt((value) => value + 1)}>
                다시 시도
              </button>
            </p>
          )}
          {!loading && !error && items.length > 0 && (
            <>
              <IssueFeed
                key={`${source}-${page}`}
                items={items}
                surface="search_results"
                offset={(page - 1) * PAGE_SIZE}
              />
              {totalPages > 1 && (
                <IssuePagination page={page} totalPages={totalPages} onChange={changePage} />
              )}
            </>
          )}
          {!loading && !error && items.length === 0 && (
            <p className="issue-state">검색된 소식이 없습니다.</p>
          )}
        </section>
      )}
    </main>
  );
}

export default SearchPage;
