import type { StockRelatedContentsResponse } from '../../stock-content/types';

export const examplePortfolio = {
  holdings: [
    { assetId: 900001, name: '삼성전자' },
    { assetId: 900002, name: '엔비디아' },
    { assetId: 900003, name: '애플' },
  ],
  loading: false,
  error: '',
  retry: () => {},
};

const titles = [
  '삼성전자, 반도체 영업이익 감소…메모리 가격 하락 영향',
  '엔비디아, 주요 고객사 AI 서버 주문 확대…공급 일정이 관건',
  '애플, 중국 매출 감소…서비스 성장에도 전체 매출 둔화',
];

export const exampleResponses: StockRelatedContentsResponse[] = examplePortfolio.holdings.map(
  (holding, index) => ({
    assetId: holding.assetId,
    assetCode: `EXAMPLE-${index}`,
    assetName: holding.name,
    contents: [
      {
        id: holding.assetId,
        assetId: holding.assetId,
        sourceType: 'INTERNAL_NEWS',
        title: titles[index],
        url: `/news/${holding.assetId}?data=example&from=portfolio`,
        imageUrl: null,
        createdAt: `2026-10-23T0${9 - index}:00:00+09:00`,
      },
    ],
  }),
);

// Fictional scenarios for the news UI preview, not factual company reporting.
export const exampleBodies: Record<number, string[]> = {
  900001: [
    '가상 시나리오입니다. 삼성전자가 이번 분기 반도체 영업이익 감소를 발표하고, 범용 메모리 가격 하락을 주요 원인으로 설명했다고 가정했습니다. 고부가 메모리 매출은 늘었지만 범용 제품의 수익성 하락을 모두 상쇄하지 못한 상황입니다.',
    '확인할 부분은 가격 하락이 다음 분기에도 이어질지, 고부가 제품의 매출 비중이 얼마나 빠르게 늘어날지입니다. 삼성전자뿐 아니라 반도체 관련 종목을 함께 보유하고 있다면, 같은 업황 변화에 영향을 받는 자산의 비중도 살펴볼 이유가 생깁니다.',
  ],
  900002: [
    '가상 시나리오입니다. 엔비디아의 주요 고객사가 AI 서버 주문을 확대했다고 가정했습니다. 신규 주문은 늘었지만 일부 제품의 납품이 다음 분기로 넘어가면서, 주문 증가와 실제 매출 반영 시점에 차이가 생긴 상황입니다.',
    '이번 소식에서는 수요 확대와 공급 지연을 함께 확인해야 합니다. 다른 AI·반도체 종목도 보유하고 있다면, 같은 투자 흐름에 연결된 종목이 포트폴리오에서 얼마나 큰 비중을 차지하는지도 확인할 만합니다.',
  ],
  900003: [
    '가상 시나리오입니다. 애플이 중국 지역 매출 감소와 전체 매출 성장 둔화를 발표했다고 가정했습니다. 서비스 매출은 증가했지만 기기 판매 감소를 충분히 메우지 못한 상황입니다.',
    '지역별 수요 둔화가 일시적인지, 서비스 성장으로 수익성을 유지할 수 있는지가 확인할 부분입니다. 다른 기술주도 보유하고 있다면, 개별 기업의 실적 변화와 기술주 전체의 보유 비중을 함께 살펴볼 수 있습니다.',
  ],
};
