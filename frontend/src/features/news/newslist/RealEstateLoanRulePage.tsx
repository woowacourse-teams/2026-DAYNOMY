import { Header } from '../../../components/Header';
import defaultNewsImage from '../../../assets/default-news-real-estate.png';
import { getCategoryLabel } from './constants';
import { formatDate } from './utils';
import './realEstateLoanRulePage.css';

const articleMeta = {
  category: 'POLICY',
  thumbnailUrl:
    'https://images.unsplash.com/photo-1582407947304-fd86f028f716?auto=format&fit=crop&w=1600&q=80',
  publishedAt: '2026-08-13T09:20:00+09:00',
};

const summaryItems = [
  '대출 규제 완화 기대는 부동산 거래 회복과 은행 대출 성장 기대를 동시에 자극합니다.',
  '다만 가계부채 관리 기조가 유지되며 실제 완화 강도는 제한적일 수 있습니다.',
];

const bodyParagraphs = [
  '정부가 부동산 대출 규제 완화 가능성을 검토하고 있다는 뉴스는 주택 시장의 유동성 기대를 자극하는 정책 이벤트로 해석됩니다. 최근 부동산 시장은 지역별로 거래량 회복 속도가 엇갈리고 있고, 고금리 부담과 대출 규제로 인해 실수요자의 매수 여력이 제한돼 있었습니다. 이런 상황에서 대출 문턱이 낮아질 수 있다는 신호가 나오면 시장은 먼저 거래 회복 가능성에 반응합니다.',
  '이 뉴스가 중요한 이유는 영향이 여러 자산군으로 동시에 번질 수 있기 때문입니다. 은행주는 대출 성장 기대를 받을 수 있고, 건설주는 분양 심리 개선과 신규 수주 기대가 반영될 수 있습니다. 부동산 관련 ETF나 리츠 역시 거래 회복 기대가 커질 때 관심을 받을 수 있습니다. 반면 채권은 금리 방향이 명확해지기 전까지 중립적으로 볼 필요가 있으며, 금은 위험자산 선호가 강해질 경우 단기 매력이 낮아질 수 있습니다.',
  '투자 관점에서 가장 먼저 확인해야 할 부분은 완화의 범위입니다. 예를 들어 생애최초 주택구입자나 무주택 실수요자 중심의 제한적 완화라면 정책 효과는 거래량 회복에 일부 기여하되 투기적 수요를 크게 자극하지 않는 방향으로 나타날 수 있습니다. 반대로 DSR 산정 방식이나 LTV 한도처럼 대출 가능 금액을 넓게 바꾸는 조치가 포함된다면 시장의 반응은 더 강해질 수 있습니다.',
  '은행주의 경우 대출 성장 기대는 분명한 호재입니다. 그러나 은행이 실제로 공격적인 증가를 받으려면 대출 증가와 함께 연체율이 안정적으로 관리돼야 합니다. 가계부채가 빠르게 늘거나 취약 차주의 상환 부담이 커지면, 대출 성장은 오히려 건전성 우려로 해석될 수 있습니다.',
  '다만 이 뉴스만으로 부동산 시장의 추세 전환을 단정하기는 어렵습니다. 정책이 실제로 시행되더라도 완화 대상이 실수요자 중심인지, 다주택자까지 포함되는지, DSR·LTV 조정 폭이 어느 정도인지에 따라 시장 반응은 크게 달라질 수 있습니다. 따라서 이번 뉴스는 즉각적인 매수 신호라기보다, 향후 정책 세부안과 거래량 데이터를 확인해야 하는 관찰 신호로 보는 것이 적절합니다.',
];

const analysisCards = [
  {
    title: '1. 이슈의 핵심 내용',
    text: '정부가 부동산 시장 회복을 위해 대출 규제 완화 가능성을 검토하고 있다는 점이 핵심입니다. 시장은 이를 주택 매수자의 자금 조달 여건이 일부 개선될 수 있다는 신호로 받아들일 수 있습니다.',
  },
  {
    title: '2. 이슈가 중요한 이유',
    text: '대출 규제는 부동산 거래량, 은행 대출 성장, 건설사 분양 심리, 채권 금리 기대에 동시에 연결됩니다. 특히 국내 투자자는 부동산과 금융주 비중이 높은 경우가 많아 포트폴리오에 영향을 줄 수 있습니다.',
  },
];

const assetImpacts = [
  {
    label: '3. 은행',
    text: '대출 수요 회복 기대가 생기면 은행주는 긍정적으로 반응할 수 있습니다. 하지만 가계대출 증가가 연체율 상승으로 이어지면 효과가 약해질 수 있습니다.',
    tone: 'positive',
  },
  {
    label: '3. 건설·부동산',
    text: '거래량 회복 기대는 건설주, 리츠, 부동산 관련 자산에 긍정적입니다. 다만 미분양이 많은 지역은 반응이 제한적일 수 있습니다.',
    tone: 'positive',
  },
  {
    label: '3. 채권',
    text: '부동산 경기 부양 기대는 위험자산 선호를 높일 수 있지만 금리 방향이 확정된 것은 아닙니다. 한국은행 기준금리와 국채 흐름을 함께 확인해야 합니다.',
    tone: 'neutral',
  },
  {
    label: '3. 금',
    text: '정책 기대가 위험자산 선호로 이어지면 금 같은 안전자산의 단기 매력은 낮아질 수 있습니다. 다만 환율 상승이나 지정학 리스크가 커지면 방어 자산 역할은 유지됩니다.',
    tone: 'negative',
  },
];

const scenarios = [
  {
    title: '단기: 기대감 선반영',
    probability: '가능성 60%',
    text: '정책 검토 뉴스만으로도 은행주, 건설주, 리츠가 먼저 반응할 수 있습니다. 다만 실제 정책 내용이 나오기 전까지는 기대감 중심의 움직임이라 변동성이 큽니다.',
  },
  {
    title: '중기: 거래량 확인 구간',
    probability: '가능성 45%',
    text: '정책이 실제 시행되면 4~8주 뒤 주택 거래량과 대출 신청 건수가 핵심 지표가 됩니다. 거래량이 동반 회복되면 부동산·건설·은행 자산의 효과가 유지될 수 있습니다.',
  },
  {
    title: '장기: 부채 부담 재부각',
    probability: '가능성 35%',
    text: '가계부채 증가세가 빨라지거나 연체율이 상승하면 정부가 다시 관리 기조를 강화할 수 있습니다. 이 경우 초기 호재가 약해지고 채권·현금성 자산 선호가 다시 높아질 수 있습니다.',
  },
];

export function RealEstateLoanRulePage() {
  return (
    <main className="loan-detail">
      <Header />

      <button
        type="button"
        className="loan-back-button"
        onClick={() => window.location.assign('/')}
      >
        ← 돌아가기
      </button>

      <article>
        <div className="loan-meta">
          <span>{getCategoryLabel(articleMeta.category)}</span>
          <time dateTime={articleMeta.publishedAt}>{formatDate(articleMeta.publishedAt)}</time>
        </div>

        <h1>부동산 대출 규제 완화 검토, 은행·건설 업종 기대감 확대</h1>

        <img
          src={articleMeta.thumbnailUrl ?? defaultNewsImage}
          alt=""
          className="loan-hero-image"
        />

        <section className="loan-section">
          <h2>핵심 요약</h2>
          <div className="summary-box">
            {summaryItems.map((item) => (
              <p key={item}>{item}</p>
            ))}
          </div>
        </section>

        <section className="loan-section">
          <h2>AI 본문</h2>
          <div className="article-copy">
            {bodyParagraphs.map((paragraph) => (
              <p key={paragraph}>{paragraph}</p>
            ))}
          </div>
        </section>

        <section className="loan-section">
          <h2>시장 분석</h2>
          <div className="analysis-stack">
            {analysisCards.map((card) => (
              <section className="analysis-card" key={card.title}>
                <h3>{card.title}</h3>
                <p>{card.text}</p>
              </section>
            ))}

            {assetImpacts.map((impact) => (
              <section className={`asset-impact ${impact.tone}`} key={impact.label}>
                <strong>{impact.label}</strong>
                <p>{impact.text}</p>
              </section>
            ))}

            <section className="analysis-card">
              <h3>4. 예상되는 긍정적·부정적 영향</h3>
              <p>
                <b className="positive-text">긍정적 영향:</b> 주택 거래 회복 기대, 은행 대출 성장
                기대, 건설사 분양 심리 개선, 부동산 관련 ETF·리츠 관심 증가가 나타날 수 있습니다.
              </p>
              <p>
                <b className="negative-text">부정적 영향:</b> 가계부채 우려가 다시 커지면 규제 완화
                폭이 제한될 수 있고, 연체율 상승 또는 금리 변동이 나타나면 금융·부동산 관련 자산의
                상승폭이 줄어들 수 있습니다.
              </p>
            </section>

            <section className="analysis-card">
              <h3>5. 단기·중기·장기 시나리오</h3>
              <div className="scenario-stack">
                {scenarios.map((scenario) => (
                  <div className="scenario-card" key={scenario.title}>
                    <div>
                      <strong>{scenario.title}</strong>
                      <span>{scenario.probability}</span>
                    </div>
                    <p>{scenario.text}</p>
                  </div>
                ))}
              </div>
            </section>

            <section className="analysis-card muted">
              <h3>6. 각 시나리오의 가능성과 판단 근거</h3>
              <p>
                단기 가능성을 가장 높게 보는 이유는 정책 검토 뉴스가 실제 수치보다 먼저 주가
                기대감에 반영되는 경우가 많기 때문입니다.
              </p>
              <p>
                중기 가능성은 정책의 세부 조건에 달려 있습니다. DSR·LTV 완화 폭이 크고 수도권
                거래량이 회복되면 경기 효과가 유지될 수 있습니다.
              </p>
              <p>
                장기 리스크는 가계부채와 연체율입니다. 대출 증가가 건전성 악화로 이어지면 은행주
                효과는 약해지고, 부동산 정책도 다시 보수적으로 바뀔 수 있습니다.
              </p>
            </section>
          </div>
        </section>
      </article>
    </main>
  );
}
