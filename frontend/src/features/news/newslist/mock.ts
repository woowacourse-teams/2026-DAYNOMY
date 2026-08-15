import type { NewsArticle, NewsCategory, NewsCategoryOption, NewsPage } from './types'

export const NEWS_CATEGORIES: NewsCategoryOption[] = [
  { label: '전체', value: 'ALL' },
  { label: '방안·개편', value: 'POLICY' },
  { label: '부동산', value: 'REAL_ESTATE' },
  { label: '금리', value: 'INTEREST_RATE' },
  { label: '환율', value: 'EXCHANGE_RATE' },
  { label: '기업 발표', value: 'COMPANY' },
  { label: '가상자산', value: 'VIRTUAL_ASSET' },
  { label: '금', value: 'GOLD' },
];

const NEWS_CATEGORY_LABELS: Record<string, string> = {
  ALL: '전체',
  POLICY: '방안·개편',
  REAL_ESTATE: '부동산',
  INTEREST_RATE: '금리',
  EXCHANGE_RATE: '환율',
  COMPANY: '기업 발표',
  VIRTUAL_ASSET: '가상자산',
  GOLD: '금',
};

const todayNews: NewsArticle = {
  id: 'real-estate-loan-rule',
  title: '부동산 대출 규제 완화 검토',
  summary: '은행주와 건설주에는 단기 호재, 채권은 중립으로 분석됩니다.',
  category: 'POLICY',
  thumbnailUrl:
    'https://images.unsplash.com/photo-1582407947304-fd86f028f716?auto=format&fit=crop&w=1600&q=80',
  publishedAt: '2026-08-13T09:20:00+09:00',
  source: '시장 분석 공개',
  body: '정부가 부동산 대출 규제 완화 가능성을 검토하고 있다는 뉴스는 주택 시장의 유동성 기대를 자극하는 정책 이벤트로 해석됩니다. 최근 부동산 시장은 지역별로 거래량 회복 속도가 엇갈리고 있고, 고금리 부담과 대출 규제로 인해 실수요자의 매수 여력이 제한돼 있었습니다. 이런 상황에서 대출 문턱이 낮아질 수 있다는 신호가 나오면 시장은 먼저 거래 회복 가능성에 반응합니다. 은행주는 대출 성장 기대가 반영될 수 있고, 건설주는 분양 심리 개선 가능성이 부각될 수 있습니다. 다만 정책이 실제 시행될지, 적용 대상과 한도가 어떻게 정해질지는 아직 불확실합니다.',
};

export const dummyNews: NewsArticle[] = [
  {
    id: 'seoul-apartment-volume',
    title: '서울 주요 지역 아파트 거래량 회복 리츠와 건설주 관심 확대',
    summary: '거래량 증가는 부동산 경기 회복 신호로 해석되며 운용사와 대출 영향이 함께 연결됩니다.',
    category: 'REAL_ESTATE',
    thumbnailUrl:
      'https://images.unsplash.com/photo-1560518883-ce09059eeffa?auto=format&fit=crop&w=640&q=80',
    publishedAt: '2026-08-14T09:05:00+09:00',
    source: '부동산 브리핑',
  },
  {
    id: 'base-rate-hold',
    title: '기준금리 동결 가능성 확대, 채권형 상품 가격 변동 기대',
    summary: '금리 하락 기대가 커질수록 장기 채권과 배당주 점검이 필요합니다.',
    category: 'INTEREST_RATE',
    thumbnailUrl:
      'https://images.unsplash.com/photo-1526304640581-d334cdbbf45e?auto=format&fit=crop&w=640&q=80',
    publishedAt: '2026-08-14T08:40:00+09:00',
    source: '금리 리포트',
  },
  {
    id: 'usd-krw-rise',
    title: '원달러 환율 상승, 수출 대형주와 해외 ETF 영향 엇갈려',
    summary: '환율 방향성은 수출주 실적과 해외 자산 평가액을 함께 확인해야 합니다.',
    category: 'EXCHANGE_RATE',
    thumbnailUrl:
      'https://images.unsplash.com/photo-1580519542036-c47de6196ba5?auto=format&fit=crop&w=640&q=80',
    publishedAt: '2026-08-14T10:10:00+09:00',
    source: '환율 체크',
  },
  {
    id: 'samsung-earnings',
    title: '삼성전자 실적 발표 임박, 반도체 ETF 변동성 커질 가능성',
    summary: '실적 개선 기대는 있지만 재고와 가격 회복 속도가 핵심 변수입니다.',
    category: 'COMPANY',
    thumbnailUrl:
      'https://images.unsplash.com/photo-1486406146926-c627a92ad1ab?auto=format&fit=crop&w=640&q=80',
    publishedAt: '2026-08-13T18:30:00+09:00',
    source: '기업 공시',
  },
  {
    id: 'gold-price-correction',
    title: '국제 금 가격 단기 조정, 안전자산 비중 확대는 신중론',
    summary: '금리와 달러 흐름이 가격에 부담을 줄 수 있어 분할 접근이 필요합니다.',
    category: 'GOLD',
    thumbnailUrl:
      'https://images.unsplash.com/photo-1610375461369-d613b564e2ba?auto=format&fit=crop&w=640&q=80',
    publishedAt: '2026-08-13T22:15:00+09:00',
    source: '원자재 노트',
  },
  {
    id: 'housing-finance-policy',
    title: '주택 금융 지원 방안 개편 논의, 실수요자 대출 조건 완화 검토',
    summary: '정책 금융 확대 여부에 따라 은행주와 건설주의 반응이 갈릴 수 있습니다.',
    category: 'POLICY',
    thumbnailUrl:
      'https://images.unsplash.com/photo-1517048676732-d65bc937f952?auto=format&fit=crop&w=640&q=80',
    publishedAt: '2026-08-14T06:50:00+09:00',
    source: '정책 뉴스',
  },
  {
    id: 'bitcoin-volatility',
    title: '비트코인 변동성 확대, 위험자산 선호 약화 신호로 해석',
    summary: '고위험 자산 비중이 높은 투자자는 달러와 금 흐름을 함께 확인할 필요가 있습니다.',
    category: 'VIRTUAL_ASSET',
    thumbnailUrl:
      'https://images.unsplash.com/photo-1621761191319-c6fb62004040?auto=format&fit=crop&w=640&q=80',
    publishedAt: '2026-08-13T16:45:00+09:00',
    source: '가상자산 브리핑',
  },
];

export function getCategoryLabel(value: string) {
  return NEWS_CATEGORY_LABELS[value] ?? value;
}

export function getDummyTodayNews() {
  return todayNews
}

export function getDummyNews(
  category: NewsCategory = 'ALL',
  page = 0,
  size = 10,
): NewsPage {
  const filteredNews =
    category === 'ALL'
      ? dummyNews
      : dummyNews.filter((article) => article.category === category)
  const start = page * size
  const content = filteredNews.slice(start, start + size)

  return {
    content,
    page,
    size,
    totalPages: Math.max(Math.ceil(filteredNews.length / size), 1),
    totalElements: filteredNews.length,
  };
}
