import newsDetailMainImage from '../../../assets/news-detail-main.jpg';
import type { NewsDetailPayload } from './types';

const detailContent = [
  '시장 변동성이 커지면서 여러 자산에 나누어 투자할 수 있는 ETF로 자금이 유입되고 있습니다. 개별 종목의 등락 위험을 줄이려는 투자자들이 분산형 상품을 대안으로 찾는 흐름입니다.',
  'ETF는 하나의 상품으로 여러 종목이나 자산에 투자할 수 있어 투자 진입 장벽을 낮춰줍니다. 특히 시장 방향을 예측하기 어려운 시기에는 특정 종목에 대한 의존도를 낮추고 포트폴리오를 분산하는 데 활용될 수 있습니다.',
  '다만 ETF 자금 유입이 모든 상품의 수익률 개선으로 이어지는 것은 아닙니다. 추종하는 지수와 편입 자산, 보수, 거래량에 따라 성과와 위험이 달라지므로 상품의 구성과 비용을 함께 확인해야 합니다.',
  '주식형 ETF는 반도체와 성장주처럼 특정 업종에 집중된 상품일수록 시장 분위기와 기초자산의 실적 전망에 민감하게 반응할 수 있습니다. 반대로 채권형이나 배당형 ETF는 변동성을 일부 낮추는 역할을 기대할 수 있지만 금리와 신용 위험을 고려해야 합니다.',
  '전문가들은 ETF를 활용할 때 단기 자금 유입만 따라가기보다 투자 기간과 위험 감내 수준에 맞는 상품인지 먼저 확인해야 한다고 조언합니다. 거래량이 충분한지, 지수를 얼마나 충실히 따라가는지도 함께 살펴볼 필요가 있습니다.',
  '당분간 시장은 변동성이 유지되는 가운데 분산 투자 수요가 ETF 시장을 지지할 가능성이 있습니다. 다만 금리와 기업 실적, 자금 흐름이 바뀌면 상품별 성과 차이가 커질 수 있어 정기적인 점검이 중요합니다.',
];

export function getMockNewsDetail(newsId: string): NewsDetailPayload {
  return {
    news: {
      id: Number.parseInt(newsId.replace(/\D/g, ''), 10) || 1,
      title: 'ETF 순자산 증가, 분산 투자 수요 확대',
      category: 'ETF',
      publishedAt: '2026-08-24T00:00:00+09:00',
      description:
        '시장 변동성이 커지며 ETF 자금 유입이 이어집니다. 분산형 상품 선호가 강해지고 있습니다.',
      content: detailContent,
      imageUrl: newsDetailMainImage,
      source: 'DAYNOMY',
      sourceUrl: null,
      originalUrl: null,
    },
    keywords: [
      {
        category: 'TERM',
        keyword: 'ETF',
        points: [
          '여러 자산을 하나의 상품으로 묶어 거래하는 펀드입니다.',
          '주식처럼 거래하면서 분산 투자 효과를 기대할 수 있습니다.',
          '추종 지수와 보수, 거래량을 함께 확인해야 합니다.',
        ],
      },
      {
        category: 'TERM',
        keyword: '분산 투자',
        points: [
          '여러 자산에 자금을 나누어 투자하는 방식입니다.',
          '개별 종목에 집중되는 위험을 낮추는 데 도움이 될 수 있습니다.',
          '분산만으로 시장 위험이 사라지는 것은 아닙니다.',
        ],
      },
      {
        category: 'TREND',
        keyword: '변동성 확대',
        points: [
          '자산 가격의 움직임이 커지는 흐름입니다.',
          '분산형 상품에 대한 관심을 높이는 요인이 될 수 있습니다.',
          '상품별 기초자산과 위험 수준은 다를 수 있습니다.',
        ],
      },
    ],
    marketAnalysis: {
      status: 'success',
      data: {
        summary:
          '시장 변동성이 커지고 개별 종목에 대한 불확실성이 높아지면서 여러 자산에 나누어 투자하려는 수요가 확대되고 있습니다. ETF는 한 상품 안에 여러 종목이나 자산을 담아 분산 투자할 수 있어 변동성 국면에서 포트폴리오를 관리하는 수단으로 활용될 수 있습니다. 다만 추종 지수와 편입 자산에 따라 위험과 수익률이 달라집니다.',
      },
    },
  };
}
