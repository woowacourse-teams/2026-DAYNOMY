export type FinancialGuide = {
  id: string;
  category: '시작' | '생활금융' | '투자' | '세금·보호';
  title: string;
  question: string;
  summary: string;
  goodFor: string[];
  caution: string[];
  checklist: string[];
  sourceLabel: string;
  sourceUrl: string;
  asOf: string;
};

export const FINANCIAL_GUIDES: FinancialGuide[] = [
  {
    id: 'first-account',
    category: '시작',
    title: '첫 통장, 무엇부터 만들까요?',
    question: '급여·생활비·비상금을 한 통장에 둬도 될까요?',
    summary:
      '급여가 들어오는 통장, 매달 쓸 생활비 통장, 쉽게 쓰지 않을 비상금 통장을 목적별로 나누는 것부터 시작해요.',
    goodFor: ['돈이 어디로 사라지는지 모르겠는 사람', '월급날마다 저축을 뒤로 미루는 사람'],
    caution: [
      '통장을 많이 만드는 것보다 자동이체 날짜를 정하는 것이 먼저예요.',
      '금리만 보고 중도해지 조건을 놓치지 마세요.',
    ],
    checklist: ['월 고정비 계산', '급여일 다음 날 자동이체 설정', '입출금 알림과 이체 한도 확인'],
    sourceLabel: '금융감독원 금융상품 한눈에',
    sourceUrl: 'https://finlife.fss.or.kr/',
    asOf: '2026-10-03',
  },
  {
    id: 'bank-types',
    category: '생활금융',
    title: '시중은행·인터넷은행·저축은행 비교',
    question: '금리가 높으면 무조건 더 좋은 은행일까요?',
    summary:
      '금리뿐 아니라 예금자보호 대상 여부, 우대 조건, 접근성, 중도해지 금리를 함께 비교해야 해요. 은행 유형보다 실제 상품 조건이 더 중요해요.',
    goodFor: ['비상금 통장을 찾는 사람', '예·적금 상품을 처음 비교하는 사람'],
    caution: [
      '표시된 최고금리는 우대 조건을 모두 채운 값일 수 있어요.',
      '상품 가입 전 예금자보호 대상 표시를 직접 확인하세요.',
    ],
    checklist: ['기본금리와 최고금리 구분', '우대 조건 난이도 확인', '중도해지 금리 확인'],
    sourceLabel: '금융감독원 금융상품 한눈에',
    sourceUrl: 'https://finlife.fss.or.kr/',
    asOf: '2026-10-03',
  },
  {
    id: 'credit-card-20s',
    category: '생활금융',
    title: '20대, 신용카드를 쓰는 게 좋을까요?',
    question: '혜택과 소비 통제 중 무엇을 먼저 봐야 할까요?',
    summary:
      '매달 전액 결제가 가능하고 소비 한도를 스스로 지킬 수 있을 때만 신용카드 혜택을 비교해요. 그렇지 않다면 체크카드가 더 안전한 선택이에요.',
    goodFor: [
      '고정 지출이 분명하고 매달 전액 결제할 수 있는 사람',
      '연회비보다 큰 실사용 혜택을 계산할 수 있는 사람',
    ],
    caution: [
      '리볼빙과 현금서비스는 일상 결제 수단으로 사용하지 마세요.',
      '전월 실적과 할인 한도를 함께 계산해야 해요.',
    ],
    checklist: ['월 카드 예산 설정', '결제일 전액 납부 설정', '연회비·전월 실적·할인 한도 비교'],
    sourceLabel: '여신금융협회 카드상품 비교',
    sourceUrl: 'https://gongsi.crefia.or.kr/portal/financialProdInfo/cardProd',
    asOf: '2026-10-03',
  },
  {
    id: 'brokerage-account',
    category: '투자',
    title: '첫 증권계좌 개설 순서',
    question: '수수료가 싸다는 이유만으로 골라도 될까요?',
    summary:
      '투자할 시장, 이체 편의, 거래 수수료와 환전 비용, ISA 같은 계좌 유형을 먼저 정하고 본인에게 필요한 계좌만 만들어요.',
    goodFor: [
      '소액으로 국내 주식이나 ETF를 연습하려는 사람',
      '장기 투자 계좌를 따로 관리하려는 사람',
    ],
    caution: [
      '이벤트 수수료의 적용 기간과 종료 후 수수료를 확인하세요.',
      '타인의 추천 링크보다 공식 앱과 공시를 이용하세요.',
    ],
    checklist: ['투자 목적과 기간 작성', '계좌 유형 확인', '수수료·환전 조건 저장'],
    sourceLabel: '금융투자협회 전자공시서비스',
    sourceUrl: 'https://dis.kofia.or.kr/',
    asOf: '2026-10-03',
  },
  {
    id: 'first-order',
    category: '투자',
    title: '첫 주문 전에 알아둘 것',
    question: '시장가와 지정가는 어떻게 다를까요?',
    summary:
      '시장가는 체결을 우선하고 지정가는 가격을 우선해요. 초보자는 소액으로 주문 방식과 체결 내역을 확인한 뒤 금액을 늘리는 편이 안전해요.',
    goodFor: ['주문 화면의 용어가 낯선 사람', '첫 주문 전 과정을 먼저 익히려는 사람'],
    caution: [
      '시장가는 예상보다 불리한 가격에 체결될 수 있어요.',
      '한 종목에 생활비나 비상금을 넣지 마세요.',
    ],
    checklist: ['주문 가능 시간 확인', '수량·가격 다시 확인', '체결 후 평균단가 기록'],
    sourceLabel: '한국거래소 주식시장 안내',
    sourceUrl: 'https://main.krxverse.co.kr/',
    asOf: '2026-10-03',
  },
  {
    id: 'diversification-etf',
    category: '투자',
    title: '한 종목보다 분산이 먼저인 이유',
    question: 'ETF 하나면 무조건 분산투자일까요?',
    summary:
      'ETF도 추종 지수와 상위 종목 비중에 따라 쏠림이 생길 수 있어요. 자산·시장·기간을 나누고 내가 이해하는 범위에서 투자해요.',
    goodFor: ['개별 기업 분석이 어려운 초보자', '정기적으로 같은 금액을 장기 투자하려는 사람'],
    caution: [
      '레버리지·인버스 상품은 장기 적립식과 성격이 달라요.',
      '과거 수익률은 미래 수익을 보장하지 않아요.',
    ],
    checklist: ['추종 지수 확인', '총보수와 상위 종목 비중 확인', '월 투자 한도 설정'],
    sourceLabel: 'FINRA 자산배분과 분산투자',
    sourceUrl:
      'https://www.finra.org/investors/investing/investing-basics/asset-allocation-diversification',
    asOf: '2026-10-03',
  },
  {
    id: 'stock-tax',
    category: '세금·보호',
    title: '국내주식·해외주식 세금 차이',
    question: '수익이 나면 모두 같은 세금을 낼까요?',
    summary:
      '국내 상장주식과 해외주식은 양도소득 과세 방식이 달라요. 배당소득과 거래비용도 별도로 보고, 실제 신고 전에는 최신 국세청 기준을 확인해야 해요.',
    goodFor: ['해외주식 거래를 시작하려는 사람', '매도 전 세후 수익을 가늠하려는 사람'],
    caution: [
      '보유자 지위와 상품 유형에 따라 과세가 달라질 수 있어요.',
      '해외주식 손익과 기본공제는 연간 합산 기준으로 확인하세요.',
    ],
    checklist: ['연간 실현손익 기록', '배당 내역 구분', '신고 일정과 최신 기준 확인'],
    sourceLabel: '국세청 양도소득세 안내',
    sourceUrl: 'https://j.nts.go.kr/nts/cm/cntnts/cntntsView.do?cntntsId=8800&mi=12274',
    asOf: '2026-10-03',
  },
  {
    id: 'investment-safety',
    category: '세금·보호',
    title: '투자 권유와 사기 신호 구분하기',
    question: '원금 보장과 고수익을 함께 약속한다면?',
    summary:
      '원금 보장·확정 고수익·비공개 정보·입금 재촉이 함께 나오면 중단하고 제도권 금융회사인지 공식 채널에서 확인해요.',
    goodFor: [
      '단체 대화방이나 SNS 투자 권유를 받은 사람',
      '처음 보는 플랫폼에 송금을 요구받은 사람',
    ],
    caution: [
      '화면의 수익 숫자만으로 실제 출금 가능 여부를 알 수 없어요.',
      '앱 설치나 원격제어 요청에 응하지 마세요.',
    ],
    checklist: ['금융회사 등록 여부 확인', '개인 계좌 송금 거절', '의심 링크와 앱 설치 중단'],
    sourceLabel: '금융감독원 파인',
    sourceUrl: 'https://fine.fss.or.kr/',
    asOf: '2026-10-03',
  },
];

export function findFinancialGuide(id: string | undefined) {
  return FINANCIAL_GUIDES.find((guide) => guide.id === id);
}
