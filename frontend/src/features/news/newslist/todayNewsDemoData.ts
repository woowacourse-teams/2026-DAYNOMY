import type { NewsListItem } from './types';

const DEMO_TODAY_NEWS: NewsListItem[] = [
  {
    id: 'demo-today-01',
    title: 'AI 반도체 수요 확대에 국내 장비주 투자심리 개선',
    category: 'STOCK',
    imageUrl: null,
    publishedAt: '2026-09-23T11:08:00+09:00',
  },
  {
    id: 'demo-today-02',
    title: '서울 아파트 거래량 회복세…강남권 중심으로 상승폭 확대',
    category: 'REAL_ESTATE',
    imageUrl: null,
    publishedAt: '2026-09-23T10:56:00+09:00',
  },
  {
    id: 'demo-today-03',
    title: '로봇·자율주행 테마 ETF에 개인 투자자 자금 유입',
    category: 'ETF',
    imageUrl: null,
    publishedAt: '2026-09-23T10:42:00+09:00',
  },
  {
    id: 'demo-today-04',
    title: '원달러 환율, 미국 금리 전망에 장중 변동성 확대',
    category: 'FOREIGN_EXCHANGE',
    imageUrl: null,
    publishedAt: '2026-09-23T10:31:00+09:00',
  },
  {
    id: 'demo-today-05',
    title: '한국은행, 물가 경로 점검…시장에서는 금리 동결 전망',
    category: 'ECONOMY',
    imageUrl: null,
    publishedAt: '2026-09-23T10:18:00+09:00',
  },
  {
    id: 'demo-today-06',
    title: '국내 채권 금리 하락세 이어져…안전자산 선호 강화',
    category: 'BOND',
    imageUrl: null,
    publishedAt: '2026-09-23T10:04:00+09:00',
  },
  {
    id: 'demo-today-07',
    title: '금 가격 사상 최고 수준 근접…중동 리스크에 수요 증가',
    category: 'GOLD',
    imageUrl: null,
    publishedAt: '2026-09-23T09:51:00+09:00',
  },
  {
    id: 'demo-today-08',
    title: '증권사, 하반기 코스피 전망치 잇따라 상향 조정',
    category: 'STOCK',
    imageUrl: null,
    publishedAt: '2026-09-23T09:36:00+09:00',
  },
  {
    id: 'demo-today-09',
    title: '배당주 관심 커져…고배당 기업 중심으로 매수세 유입',
    category: 'STOCK',
    imageUrl: null,
    publishedAt: '2026-09-23T09:20:00+09:00',
  },
];

const MIN_TODAY_NEWS_COUNT = 9;

export function addTodayNewsDemoData(articles: NewsListItem[]) {
  const isDevelopmentPreview = import.meta.env.DEV && import.meta.env.MODE !== 'test';

  if (!isDevelopmentPreview || articles.length >= MIN_TODAY_NEWS_COUNT) {
    return articles;
  }

  const existingIds = new Set(articles.map((article) => String(article.id)));
  const missingCount = MIN_TODAY_NEWS_COUNT - articles.length;
  const demoArticles = DEMO_TODAY_NEWS.filter(
    (article) => !existingIds.has(String(article.id)),
  ).slice(0, missingCount);

  return [...articles, ...demoArticles];
}
