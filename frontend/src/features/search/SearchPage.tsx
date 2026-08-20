import { useState } from 'react';
import './SearchPage.css';
import { trackEvent } from '../../analytics';

const PAGE_SIZE = 10;

const suggestions = [
  { keyword: '금', category: '자산' },
  { keyword: '금리', category: '지표' },
  { keyword: '금가격', category: '뉴스' },
  { keyword: '국제금리', category: '채권' },
  { keyword: '기준금리', category: '정책' },
  { keyword: '연금', category: '자산' },
];

const news = [
  {
    id: 1,
    category: '통화정책',
    title: '한국은행, 기준금리 동결…물가와 가계대출 흐름 주시',
    summary: '시장에서는 하반기 금리 인하 시점이 핵심 변수로 떠올랐습니다.',
    source: '경제일보',
    publishedAt: '2시간 전',
  },
  {
    id: 2,
    category: '원자재',
    title: '국제 금가격, 안전자산 수요에 사상 최고치 근접',
    summary: '지정학적 불확실성과 달러 약세가 금값 상승을 뒷받침했습니다.',
    source: '마켓뉴스',
    publishedAt: '3시간 전',
  },
  {
    id: 3,
    category: '글로벌',
    title: '미국 금리 인하 기대 확대…국내 증시에도 훈풍',
    summary: '성장주와 채권을 중심으로 투자 심리가 개선되는 모습입니다.',
    source: '투자경제',
    publishedAt: '4시간 전',
  },
  {
    id: 4,
    category: 'ETF',
    title: '금 ETF로 몰리는 자금, 한 달 새 순자산 크게 증가',
    summary: '개인 투자자의 금 관련 상장지수펀드 매수가 이어지고 있습니다.',
    source: '증권데일리',
    publishedAt: '5시간 전',
  },
  {
    id: 5,
    category: '환율',
    title: '원·달러 환율 하락에도 국내 금 시세는 강보합',
    summary: '국제 시세 강세가 환율 하락 영향을 일부 상쇄했습니다.',
    source: '금융투데이',
    publishedAt: '6시간 전',
  },
  {
    id: 6,
    category: '채권',
    title: '정부, 채권시장 안정 위해 국고채 발행 물량 조정',
    summary: '장기 시장금리 변동성을 낮추기 위한 수급 대책이 발표됐습니다.',
    source: '정책브리핑',
    publishedAt: '7시간 전',
  },
  {
    id: 7,
    category: '연금',
    title: '국민연금, 금과 채권 등 대체자산 비중 확대 검토',
    summary: '포트폴리오 변동성을 낮추기 위한 자산 배분 조정이 논의됩니다.',
    source: '연합뉴스경제',
    publishedAt: '8시간 전',
  },
  {
    id: 8,
    category: '부동산',
    title: '금리 인하 기대에 주택담보대출 고정금리 하락',
    summary: '대출자의 이자 부담이 줄어들 수 있다는 전망이 나왔습니다.',
    source: '부동산경제',
    publishedAt: '9시간 전',
  },
  {
    id: 9,
    category: '기업',
    title: '금광 기업 실적 개선…금가격 상승 효과 본격 반영',
    summary: '원가 안정과 판매 단가 상승이 영업이익 개선으로 이어졌습니다.',
    source: '기업리포트',
    publishedAt: '10시간 전',
  },
  {
    id: 10,
    category: '미국',
    title: '연준 위원들, 금리 결정에 물가 지표 확인 필요 강조',
    summary: '시장은 다음 고용과 소비자물가 발표에 주목하고 있습니다.',
    source: '글로벌경제',
    publishedAt: '11시간 전',
  },
  {
    id: 11,
    category: '가계금융',
    title: '금융당국, 가계대출 금리 산정 체계 점검 착수',
    summary: '은행별 가산금리 차이와 소비자 부담을 살펴볼 예정입니다.',
    source: '금융소식',
    publishedAt: '12시간 전',
  },
  {
    id: 12,
    category: '외환보유액',
    title: '각국 중앙은행 금 보유량 확대…장기 수요 견조',
    summary: '외환보유액 다변화 움직임이 국제 금 수요를 지지하고 있습니다.',
    source: '월드마켓',
    publishedAt: '13시간 전',
  },
  {
    id: 13,
    category: '회사채',
    title: '시장금리 안정에 우량 회사채 발행 잇따라',
    summary: '기업들이 낮아진 조달 금리를 활용해 선제적으로 자금을 확보합니다.',
    source: '채권정보',
    publishedAt: '14시간 전',
  },
  {
    id: 14,
    category: '선물',
    title: '금 선물 거래량 증가…단기 변동성 확대 주의',
    summary: '차익 실현 물량과 신규 매수가 맞물리며 가격 폭이 커졌습니다.',
    source: '원자재포커스',
    publishedAt: '15시간 전',
  },
  {
    id: 15,
    category: '전망',
    title: '하반기 금가격 전망, 실질금리와 달러 방향이 좌우',
    summary: '전문가들은 금리 경로와 중앙은행 수요를 주요 지표로 꼽았습니다.',
    source: '경제전망',
    publishedAt: '16시간 전',
  },
];

function getPage<T>(items: T[], page: number) {
  return items.slice((page - 1) * PAGE_SIZE, page * PAGE_SIZE);
}

if (import.meta.env.DEV) {
  console.assert(getPage(Array.from({ length: 11 }), 2).length === 1);
}

function SearchIcon() {
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true">
      <circle cx="11" cy="11" r="6.5" />
      <path d="m16 16 4 4" />
    </svg>
  );
}

function SearchPage() {
  const [query, setQuery] = useState('금');
  const [searchedKeyword, setSearchedKeyword] = useState('금');
  const [page, setPage] = useState(1);
  const keyword = query.trim();
  // ponytail: local data until the search API is available; replace these filters with fetch.
  const related = suggestions.filter(
    ({ keyword: item }) => keyword !== '' && item.includes(keyword),
  );
  const results = news.filter(({ title, summary, category }) =>
    `${title} ${summary} ${category}`.includes(searchedKeyword),
  );
  const totalPages = Math.ceil(results.length / PAGE_SIZE);

  const search = (value = query) => {
    const nextKeyword = value.trim();
    if (!nextKeyword) return;
    setQuery(nextKeyword);
    setSearchedKeyword(nextKeyword);
    setPage(1);
    trackEvent('search_news', { search_term: nextKeyword });
    if (!news.some(({ title, summary, category }) => `${title} ${summary} ${category}`.includes(nextKeyword))) {
      trackEvent('search_no_result', { search_term: nextKeyword });
    }
  };

  const changePage = (nextPage: number) => {
    setPage(nextPage);
    document.getElementById('news-results')?.scrollIntoView({ behavior: 'smooth' });
  };

  return (
    <main className="search-page">
      <div className="search-panel">
        <div className="search-row">
          <button
            type="button"
            className="back-button"
            aria-label="이전 화면으로 이동"
            onClick={() => window.history.back()}
          >
            ‹
          </button>

          <form
            className="search-form"
            onSubmit={(event) => {
              event.preventDefault();
              search();
            }}
          >
            <SearchIcon />
            <label className="sr-only" htmlFor="news-search">
              뉴스 키워드 검색
            </label>
            <input
              id="news-search"
              value={query}
              onChange={(event) => setQuery(event.target.value)}
              placeholder="검색어를 입력하세요"
              autoComplete="off"
            />
            <button type="submit" className="sr-only">
              검색
            </button>
          </form>
        </div>

        <section className="search-content" aria-labelledby="search-title">
          <h1 id="search-title">검색</h1>
          <p>글자를 입력하면 관련 뉴스 키워드가 바로 표시됩니다.</p>

          <ul className="suggestion-list" aria-live="polite">
            {related.map(({ keyword: item, category }) => (
              <li key={`${item}-${category}`}>
                <button type="button" onClick={() => search(item)}>
                  <span className="suggestion-keyword">
                    <SearchIcon />
                    {item}
                  </span>
                  <span className="suggestion-category">{category}</span>
                </button>
              </li>
            ))}
          </ul>

          {keyword && related.length === 0 && (
            <p className="empty-message">관련 키워드가 없습니다.</p>
          )}
        </section>

        <section id="news-results" className="news-results" aria-labelledby="result-title">
          <div className="result-heading">
            <h2 id="result-title">‘{searchedKeyword}’ 뉴스</h2>
            <span>{results.length}건</span>
          </div>

          {results.length > 0 ? (
            <>
              <ol className="news-list" start={(page - 1) * PAGE_SIZE + 1}>
                {getPage(results, page).map((item) => (
                  <li key={item.id}>
                    <article>
                      <div className="news-meta">
                        <span className="news-category">{item.category}</span>
                        <span>
                          {item.source} · {item.publishedAt}
                        </span>
                      </div>
                      <h3>{item.title}</h3>
                      <p>{item.summary}</p>
                    </article>
                  </li>
                ))}
              </ol>

              {totalPages > 1 && (
                <nav className="pagination" aria-label="검색 결과 페이지">
                  <button type="button" disabled={page === 1} onClick={() => changePage(page - 1)}>
                    이전
                  </button>
                  {Array.from({ length: totalPages }, (_, index) => index + 1).map((pageNumber) => (
                    <button
                      type="button"
                      className={page === pageNumber ? 'active' : ''}
                      aria-current={page === pageNumber ? 'page' : undefined}
                      onClick={() => changePage(pageNumber)}
                      key={pageNumber}
                    >
                      {pageNumber}
                    </button>
                  ))}
                  <button
                    type="button"
                    disabled={page === totalPages}
                    onClick={() => changePage(page + 1)}
                  >
                    다음
                  </button>
                </nav>
              )}
            </>
          ) : (
            <p className="no-results">검색 결과가 없습니다.</p>
          )}
        </section>
      </div>
    </main>
  );
}

export default SearchPage;
