import type { Impact, NewsDetail, RelatedIssue } from './types'

export const mockNews: NewsDetail = {
  title: '부동산 대출 규제 완화 검토, 은행·건설 업종 기대감 확대',
  category: '부동산',
  source: '연합 뉴스',
  publishedAt: '2026.08.13 09:20',
  originalUrl: 'https://example.com/news/1',
  summary:
    '대출 규제 완화 기대는 부동산 거래 회복과 은행 대출 성장 기대를 동시에 자극합니다. 다만 가계부채 관리 기조가 유지되며 실제 완화 강도는 제한적일 수 있습니다.',
  body: [
    '정부가 부동산 대출 규제 완화 가능성을 검토하고 있다는 뉴스가 주식 시장의 유동성 기대를 자극하는 흐름이 이어지고 있습니다. 관련 업종은 정책 변화에 민감하게 반응하고 있으며, 은행과 건설 업종을 중심으로 투자 심리가 개선되는 모습입니다.',
    '다만 완화가 확정된 것은 아니며, 금융당국은 가계부채 증가 속도와 시장 과열 여부를 함께 점검할 가능성이 큽니다. 시장은 단기적으로 기대감을 반영하되, 실제 정책 발표 전까지는 변동성이 남아 있습니다.',
    '부동산 거래 회복은 건설 수주와 분양 심리에 긍정적으로 작용할 수 있고, 은행권에는 주택담보대출 수요 증가 기대를 만들 수 있습니다. 반면 금리와 환율, 물가 흐름이 정책 여력을 제한할 수 있다는 점은 부담 요인입니다.',
  ],
}

export const mockImpacts: Impact[] = [
  {
    asset: '주식',
    direction: 'positive',
    evidence:
      '은행과 건설 업종은 대출 증가와 거래 회복 기대를 직접 반영할 수 있습니다.',
  },
  {
    asset: '채권',
    direction: 'neutral',
    evidence:
      '가계부채 관리 기조가 유지되면 금리 기대가 크게 움직이기 어렵습니다.',
  },
  {
    asset: '부동산',
    direction: 'positive',
    evidence:
      '대출 문턱 완화는 매수 심리와 거래량 회복에 가장 직접적인 재료입니다.',
  },
  {
    asset: '금',
    direction: 'neutral',
    evidence: '부동산 정책 이슈와 안전자산 수요의 연결성은 제한적입니다.',
  },
  {
    asset: '환율',
    direction: 'negative',
    evidence:
      '가계부채 확대 우려가 커지면 원화 투자 심리에 부담이 될 수 있습니다.',
  },
]

export const mockRelatedIssues: RelatedIssue[] = [
  {
    keyword: '대출 규제 완화',
    title: '대출 규제 완화',
    description:
      '정책 방향은 대출 규제 완화 가능성이지만, 실제 시행 여부와 강도는 금융당국의 가계부채 판단에 달려 있습니다.',
  },
  {
    keyword: '금융당국',
    title: '금융당국',
    probability: 60,
    description:
      '가계부채 증가 속도와 시장 과열 여부를 함께 보며 완화 강도를 조절할 가능성이 큽니다.',
  },
  {
    keyword: '건설 업종',
    title: '건설 업종',
    probability: 45,
    description:
      '거래 회복 기대가 커질수록 수주, 분양 심리, 주가 기대에 긍정적으로 반영될 수 있습니다.',
  },
]
