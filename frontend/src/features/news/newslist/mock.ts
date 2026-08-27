import newsHomeBannerImage from '../../../assets/news-home-banner.jpg';
import newsHomeCardImage1 from '../../../assets/news-home-card-1.jpg';
import newsHomeCardImage2 from '../../../assets/news-home-card-2.jpg';
import newsHomeCardImage3 from '../../../assets/news-home-card-3.jpg';
import newsHomeCardImage4 from '../../../assets/news-home-card-4.jpg';
import newsHomeCardImage5 from '../../../assets/news-home-card-5.jpg';
import type { NewsCategory, NewsListItem, NewsPage } from './types';

const cardImages = [
  newsHomeCardImage1,
  newsHomeCardImage2,
  newsHomeCardImage3,
  newsHomeCardImage4,
  newsHomeCardImage5,
];

const PAGE_SIZE = 9;

const firstPageArticles: NewsListItem[] = [
  {
    id: 'mock-foreign-exchange-1',
    title: '원·달러 환율 상승세, 수입 물가 부담 확대',
    description: '환율 상승으로 수입 가격 부담이 커지고 있습니다.',
    category: 'FOREIGN_EXCHANGE',
    publishedAt: '35분 전',
    imageUrl: cardImages[0],
  },
  {
    id: 'mock-economy-1',
    title: '기준금리 동결',
    description: '시장 관망세가 이어지고 있습니다.',
    category: 'ECONOMY',
    publishedAt: '1시간 전',
    imageUrl: cardImages[1],
  },
  {
    id: 'mock-stock-1',
    title: '코스피 외국인 매수세 둔화, 반도체·2차전지 대형주 흐름 엇갈려',
    description:
      '주요 업종별 수급 차이가 커지고 있습니다. 시장은 대형주 변동성을 함께 보고 있습니다.',
    category: 'STOCK',
    publishedAt: '1시간 전',
    imageUrl: cardImages[2],
  },
  {
    id: 'mock-deposit-savings-1',
    title: '예금 금리 하락세, 단기 상품 관심 증가',
    description:
      '만기가 짧은 예금 상품에 관심이 늘고 있습니다. 은행별 금리 차이도 다시 주목됩니다.',
    category: 'DEPOSIT_SAVINGS',
    publishedAt: '3시간 전',
    imageUrl: cardImages[3],
  },
  {
    id: 'mock-bond-1',
    title: '채권 금리 안정',
    description: '장기 국채 수요가 회복되고 있습니다.',
    category: 'BOND',
    publishedAt: '3시간 전',
    imageUrl: cardImages[4],
  },
  {
    id: 'mock-etf-1',
    title: 'ETF 순자산 증가, 분산 투자 수요 확대',
    description:
      '시장 변동성이 커지며 ETF 자금 유입이 이어집니다. 분산형 상품 선호가 강해지고 있습니다.',
    category: 'ETF',
    publishedAt: '6시간 전',
    imageUrl: cardImages[0],
  },
  {
    id: 'mock-virtual-asset-1',
    title: '비트코인 변동성 확대, 투자 심리 위축',
    description: '거래량 변화와 규제 이슈가 함께 관찰되고 있습니다.',
    category: 'VIRTUAL_ASSET',
    publishedAt: '7시간 전',
    imageUrl: cardImages[1],
  },
  {
    id: 'mock-gold-1',
    title: '금 가격 강세',
    description: '안전자산 선호가 유지되고 있습니다. 달러 흐름도 가격에 영향을 주고 있습니다.',
    category: 'GOLD',
    publishedAt: '8시간 전',
    imageUrl: cardImages[2],
  },
  {
    id: 'mock-real-estate-1',
    title: '서울 아파트 거래량 회복, 지역별·단지별 가격 흐름 차별화',
    description: '실수요와 투자 수요가 갈리는 모습입니다. 지역별 가격 흐름은 차이가 큽니다.',
    category: 'REAL_ESTATE',
    publishedAt: '9시간 전',
    imageUrl: cardImages[3],
  },
];

const secondPageArticles: NewsListItem[] = [
  {
    id: 'mock-second-page-1',
    title: '미국 물가 지표 둔화, 금리 인하 기대 재점화',
    description: '인플레이션 압력이 완화되며 글로벌 금융시장의 위험 선호가 일부 회복되고 있습니다.',
    category: 'ECONOMY',
    publishedAt: '10시간 전',
    imageUrl: cardImages[4],
  },
  {
    id: 'mock-second-page-2',
    title: '반도체 대형주 실적 전망 상향, 수급 개선 기대',
    description: 'AI 서버 투자와 메모리 가격 회복 기대가 주요 종목 흐름에 반영되고 있습니다.',
    category: 'STOCK',
    publishedAt: '12시간 전',
    imageUrl: cardImages[0],
  },
  {
    id: 'mock-second-page-3',
    title: '수도권 전세 가격 상승세, 매매 관망 흐름 지속',
    description: '전세 수요가 일부 지역에 집중되며 가격 부담이 다시 커지는 모습입니다.',
    category: 'REAL_ESTATE',
    publishedAt: '14시간 전',
    imageUrl: cardImages[1],
  },
  {
    id: 'mock-second-page-4',
    title: '국제 금값 보합권, 달러 강세에 상승폭 제한',
    description: '안전자산 수요는 유지됐지만 달러 흐름이 금 가격 상단을 누르고 있습니다.',
    category: 'GOLD',
    publishedAt: '16시간 전',
    imageUrl: cardImages[2],
  },
  {
    id: 'mock-second-page-5',
    title: '회사채 발행 재개, 우량채 중심 투자 수요 회복',
    description: '금리 변동성이 줄면서 기관 투자자의 채권 매수 움직임이 늘고 있습니다.',
    category: 'BOND',
    publishedAt: '18시간 전',
    imageUrl: cardImages[3],
  },
];

const mockArticles = [...firstPageArticles, ...secondPageArticles];

const depositSavingsArticles: NewsListItem[] = [
  {
    id: 'mock-deposit-savings-detail-1',
    title: '예금 금리 하락세, 단기 상품 관심 증가',
    description:
      '만기가 짧은 예금 상품에 관심이 늘고 있습니다. 은행별 금리 차이도 다시 주목됩니다.',
    category: 'DEPOSIT_SAVINGS',
    publishedAt: '35분 전',
    imageUrl: cardImages[0],
  },
  {
    id: 'mock-deposit-savings-detail-2',
    title: '정기예금 우대금리 축소, 갈아타기 수요 둔화',
    description: '주요 은행의 우대금리 조건이 줄어들며 예금 상품 비교 필요성이 커지고 있습니다.',
    category: 'DEPOSIT_SAVINGS',
    publishedAt: '1시간 전',
    imageUrl: cardImages[1],
  },
  {
    id: 'mock-deposit-savings-detail-3',
    title: '파킹통장 금리 경쟁 완화, 자금 이동 속도 둔화',
    description: '수시입출금 상품 금리가 낮아지면서 단기 대기자금 흐름이 조정되고 있습니다.',
    category: 'DEPOSIT_SAVINGS',
    publishedAt: '1시간 전',
    imageUrl: cardImages[2],
  },
  {
    id: 'mock-deposit-savings-detail-4',
    title: '적금 특판 조기 마감, 소액 납입 상품 인기',
    description:
      '월 납입 부담이 낮은 특판 적금에 가입 수요가 몰리며 조기 마감 사례가 늘고 있습니다.',
    category: 'DEPOSIT_SAVINGS',
    publishedAt: '3시간 전',
    imageUrl: cardImages[3],
  },
];

export function getMockNewsPage(category: NewsCategory, page = 1): NewsPage {
  const source =
    category === 'DEPOSIT_SAVINGS'
      ? depositSavingsArticles
      : category === 'ALL'
        ? mockArticles
        : mockArticles.filter((article) => article.category === category);
  const totalPages = Math.max(Math.ceil(source.length / PAGE_SIZE), 1);
  const currentPage = Math.min(Math.max(page, 1), totalPages);
  const startIndex = (currentPage - 1) * PAGE_SIZE;
  const content = source.slice(startIndex, startIndex + PAGE_SIZE);

  return {
    content,
    page: currentPage,
    size: PAGE_SIZE,
    totalPages,
    totalElements: source.length,
  };
}

export function getMockTodayNews(category: NewsCategory): NewsListItem {
  if (category === 'DEPOSIT_SAVINGS') {
    return {
      ...depositSavingsArticles[0],
      imageUrl: newsHomeBannerImage,
      publishedAt: '2026-08-24T00:00:00+09:00',
    };
  }

  return {
    ...firstPageArticles[0],
    imageUrl: newsHomeBannerImage,
    publishedAt: '2026-08-24T00:00:00+09:00',
  };
}
