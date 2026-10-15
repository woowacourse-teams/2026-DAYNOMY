import { useEffect, useRef, useState } from 'react';
import { getAdminNews } from '../api';
import type { AdminNewsPageResponse } from '../types';
import { getPublishedDate } from '../../stock-content/publishedDate';

type Props = {
  stockName: string;
  hidden: boolean;
  saving: boolean;
  isRegistered: (url: string) => boolean;
  onLink: (newsId: number) => Promise<void>;
};

export function AdminDaynomyNewsSearch({ stockName, hidden, saving, isRegistered, onLink }: Props) {
  const [keyword, setKeyword] = useState(stockName);
  const [searchedKeyword, setSearchedKeyword] = useState('');
  const [results, setResults] = useState<AdminNewsPageResponse | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const controllerRef = useRef<AbortController | null>(null);

  useEffect(() => () => controllerRef.current?.abort(), []);

  async function search(page: number, query: string) {
    controllerRef.current?.abort();
    const controller = new AbortController();
    controllerRef.current = controller;
    setLoading(true);
    setError('');
    try {
      const response = await getAdminNews(page, 'PUBLISHED', 'ALL', controller.signal, query);
      if (controller.signal.aborted) return;
      setResults(response);
      setSearchedKeyword(query);
    } catch (caughtError) {
      if (controller.signal.aborted) return;
      setError(
        caughtError instanceof Error ? caughtError.message : 'DAYNOMY 뉴스를 검색하지 못했습니다.',
      );
    } finally {
      if (!controller.signal.aborted) setLoading(false);
    }
  }

  return (
    <section
      className="admin-daynomy-search"
      hidden={hidden}
      aria-labelledby="daynomy-search-title"
    >
      <h3 id="daynomy-search-title">DAYNOMY 뉴스 검색</h3>
      <p className="admin-field-hint">
        발행된 뉴스를 찾아 {stockName}에 연결하세요. 검색어를 비우면 전체 뉴스를 볼 수 있습니다.
      </p>
      <form
        className="admin-related-asset-search"
        onSubmit={(event) => {
          event.preventDefault();
          void search(1, keyword.trim());
        }}
      >
        <label className="admin-field">
          <span>뉴스 검색어</span>
          <input
            type="search"
            value={keyword}
            maxLength={100}
            placeholder="종목명 또는 뉴스 키워드"
            onChange={(event) => setKeyword(event.target.value)}
          />
        </label>
        <button type="submit" className="admin-secondary-button" disabled={loading}>
          {loading ? '검색 중…' : 'DAYNOMY 검색'}
        </button>
      </form>
      {error ? (
        <p className="admin-field-error" role="alert">
          {error}
        </p>
      ) : null}
      {loading ? <p role="status">DAYNOMY 뉴스를 찾고 있습니다.</p> : null}
      {!results && !loading && !error ? (
        <p className="admin-picker-empty">검색 후 연결할 뉴스를 선택하세요.</p>
      ) : null}
      {results ? (
        <div aria-busy={loading}>
          <p className="admin-field-hint">
            {searchedKeyword ? `‘${searchedKeyword}’ 검색 결과` : '전체 발행 뉴스'} ·{' '}
            {results.totalElements}건
          </p>
          {results.items.length === 0 ? (
            <p className="admin-picker-empty">
              검색 결과가 없습니다. 검색어를 바꾸거나 비워서 다시 검색해 주세요.
            </p>
          ) : (
            <ul className="admin-social-results">
              {results.items.map((news) => {
                const url = `/news/${news.id}`;
                const registered = isRegistered(url);
                return (
                  <li key={news.id}>
                    <div>
                      <span>DAYNOMY · {getPublishedDate(news.publishedAt) ?? '발행일 없음'}</span>
                      <strong>{news.title}</strong>
                      <a href={url} target="_blank" rel="noreferrer">
                        뉴스 보기 ↗
                      </a>
                    </div>
                    <button
                      type="button"
                      className="admin-secondary-button"
                      disabled={saving || loading || registered}
                      onClick={() => void onLink(news.id)}
                    >
                      {registered ? '연결됨' : '이 뉴스 연결'}
                    </button>
                  </li>
                );
              })}
            </ul>
          )}
          {results.totalPages > 1 ? (
            <div className="admin-daynomy-pagination" aria-label="뉴스 검색 페이지">
              <button
                type="button"
                className="admin-secondary-button"
                disabled={loading || results.page <= 1}
                onClick={() => void search(results.page - 1, searchedKeyword)}
              >
                이전
              </button>
              <span>
                {results.page} / {results.totalPages}
              </span>
              <button
                type="button"
                className="admin-secondary-button"
                disabled={loading || !results.hasNext}
                onClick={() => void search(results.page + 1, searchedKeyword)}
              >
                다음
              </button>
            </div>
          ) : null}
        </div>
      ) : null}
    </section>
  );
}
