import newsDetailMainImage from '../../../assets/news-detail-main.jpg';
import type { NewsDetailPayload } from './types';

const detailContent = [
  '원·달러 환율이 다시 오름세를 보이면서 에너지와 원자재 수입 가격 부담이 커지고 있습니다. 최근 글로벌 달러 강세와 주요국 금리 흐름이 맞물리면서 외환시장의 변동성이 커졌고, 국내 수입 기업들은 비용 부담 확대 가능성을 주시하고 있습니다.',
  '특히 원유와 원자재처럼 달러로 거래되는 품목은 환율 상승의 영향을 직접적으로 받을 수 있습니다. 수입 가격이 오르면 기업의 생산 비용이 높아지고, 일부 비용은 소비자 가격에 반영될 수 있어 물가 흐름에도 영향을 줄 수 있습니다. 생활필수품과 에너지 가격은 소비자 체감 물가와도 연결되기 때문에 환율 변동은 가계 부담에도 영향을 미칠 수 있습니다.',
  '시장에서는 환율 변동이 단기간에 그치지 않을 경우 업종별 실적 전망에도 차이가 생길 수 있다고 보고 있습니다. 수입 비중이 높은 업종은 비용 부담이 커질 수 있고, 반대로 수출 비중이 높은 기업은 환율 효과를 일부 기대할 수 있다는 분석도 나옵니다. 다만 환율 상승이 반드시 기업 실적 개선으로 이어지는 것은 아니며, 원재료 조달 비용과 해외 수요 상황을 함께 살펴봐야 합니다.',
  '금융시장에서는 환율과 금리 흐름을 함께 확인하려는 움직임이 이어지고 있습니다. 환율이 높은 수준을 유지하면 물가 안정 속도가 더뎌질 수 있고, 이는 기준금리 인하 기대에도 영향을 줄 수 있습니다. 채권시장과 주식시장 모두 향후 물가 지표와 미국 통화정책 방향을 확인하며 움직일 가능성이 큽니다.',
  '전문가들은 단기 환율 움직임만 보고 투자 판단을 내리기보다는 국내외 금리 차, 무역수지, 글로벌 경기 전망을 종합적으로 살펴야 한다고 말합니다. 환율은 여러 요인이 동시에 반영되는 지표인 만큼, 투자자들은 관련 뉴스와 경제지표를 함께 확인하며 자산 배분을 조정할 필요가 있습니다.',
  '당분간 시장은 환율이 기업 비용과 물가에 미치는 영향을 중심으로 반영할 가능성이 큽니다. 특히 수입 원가 부담이 큰 업종과 외화 부채 비중이 높은 기업은 환율 변동에 민감하게 움직일 수 있어 관련 지표를 꾸준히 확인하는 것이 중요합니다.',
];

export function getMockNewsDetail(newsId: string): NewsDetailPayload {
  return {
    news: {
      id: Number.parseInt(newsId.replace(/\D/g, ''), 10) || 1,
      title: '원·달러 환율 상승세, 수입 물가 부담 확대',
      category: 'FOREIGN_EXCHANGE',
      publishedAt: '2026-08-24T00:00:00+09:00',
      description:
        '환율 상승으로 수입 가격 부담이 커지고 있습니다. 원자재와 에너지 가격 전가 가능성이 커지며 물가와 기업 비용에 영향을 줄 수 있습니다.',
      content: detailContent,
      imageUrl: newsDetailMainImage,
      source: 'DAYNOMY',
      sourceUrl: null,
      originalUrl: null,
    },
    keywords: [
      {
        category: 'TERM',
        keyword: '원·달러 환율',
        points: [
          '원화와 미국 달러의 교환 비율입니다.',
          '수입 물가와 기업 비용에 영향을 줄 수 있습니다.',
          '금리와 자산 시장 흐름을 함께 확인해야 합니다.',
        ],
      },
      {
        category: 'TERM',
        keyword: '수입 물가',
        points: [
          '해외에서 들여오는 상품과 원자재 가격 수준입니다.',
          '환율이 오르면 수입 비용이 커질 수 있습니다.',
          '소비자 물가에도 영향을 줄 수 있습니다.',
        ],
      },
      {
        category: 'TREND',
        keyword: '달러 강세',
        points: [
          '주요 통화 대비 달러 가치가 높아지는 흐름입니다.',
          '원화 약세와 수입 비용 증가로 이어질 수 있습니다.',
          '글로벌 금리와 위험 선호를 함께 살펴야 합니다.',
        ],
      },
    ],
    marketAnalysis: {
      cause:
        '미국 금리 불확실성과 달러 강세가 겹치며 원화 약세 압력이 커졌습니다. 수입 원가 상승은 기업 마진과 소비자 물가에 부담으로 이어질 수 있습니다.',
      assets: [
        {
          asset: 'FOREIGN_EXCHANGE',
          direction: 'POSITIVE',
          impactLevel: 'HIGH',
          reason: '환율 변동성이 커지며 외환 관련 관심과 헤지 수요가 증가합니다.',
        },
        {
          asset: 'STOCK',
          direction: 'NEGATIVE',
          impactLevel: 'MEDIUM',
          reason: '수입 원가 부담이 큰 업종은 비용 압박을 받을 수 있습니다.',
        },
        {
          asset: 'BOND',
          direction: 'NEGATIVE',
          impactLevel: 'LOW',
          reason: '물가 부담이 금리 인하 기대를 약화시킬 수 있습니다.',
        },
      ],
      scenarios: [
        {
          timeHorizon: 'SHORT_TERM',
          prediction: '환율 변동성이 이어질 가능성이 있습니다.',
          probability: 62,
          reason: '미국 경제 지표와 금리 전망이 단기 환율 방향을 좌우할 수 있습니다.',
        },
        {
          timeHorizon: 'MID_TERM',
          prediction: '수입 물가 부담이 일부 소비재 가격에 반영될 수 있습니다.',
          probability: 48,
          reason: '기업의 원가 전가 여부에 따라 소비자 체감 물가가 달라질 수 있습니다.',
        },
        {
          timeHorizon: 'LONG_TERM',
          prediction: '환율이 안정되면 원가 부담도 완화될 수 있습니다.',
          probability: 36,
          reason: '달러 강세가 둔화되고 원화가 회복되면 비용 압력이 낮아질 수 있습니다.',
        },
      ],
    },
  };
}
