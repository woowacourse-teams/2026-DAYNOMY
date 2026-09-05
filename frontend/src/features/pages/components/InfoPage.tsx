import '../InfoPage.css';

type InfoPageType = 'about' | 'terms' | 'privacy' | 'standard';

type InfoPageContent = {
  title: string;
  lead: string;
  sections: Array<{
    title: string;
    paragraphs: string[];
  }>;
};

const INFO_PAGE_CONTENT: Record<InfoPageType, InfoPageContent> = {
  about: {
    title: '회사소개',
    lead: 'DAYNOMY는 매일의 경제 흐름을 이해하기 쉽게 전합니다.',
    sections: [
      {
        title: 'DAYNOMY는',
        paragraphs: [
          '복잡한 경제 뉴스와 시장 정보를 한눈에 살펴볼 수 있도록 정리하는 서비스입니다.',
          '오늘의 주요 뉴스부터 관심종목 흐름까지, 투자 판단에 필요한 정보를 차분하게 확인할 수 있는 경험을 만들어갑니다.',
        ],
      },
      {
        title: '서비스 원칙',
        paragraphs: [
          '쉽게 읽을 수 있는 정보, 중요한 흐름을 놓치지 않는 구성, 사용자의 관심에 맞춘 탐색을 지향합니다.',
        ],
      },
    ],
  },
  terms: {
    title: '이용약관',
    lead: 'DAYNOMY 서비스 이용에 필요한 기본 안내입니다.',
    sections: [
      {
        title: '서비스 이용',
        paragraphs: [
          'DAYNOMY는 경제 뉴스와 관심종목 정보를 제공하며, 서비스 내용은 운영 상황에 따라 변경될 수 있습니다.',
          '서비스에서 제공하는 정보는 참고용이며 특정 금융상품의 매수 또는 매도를 권유하지 않습니다.',
        ],
      },
      {
        title: '안내사항',
        paragraphs: ['본 페이지는 정식 이용약관 확정 전 제공되는 임시 안내 문구입니다.'],
      },
    ],
  },
  privacy: {
    title: '개인정보처리방침',
    lead: 'DAYNOMY는 서비스 제공에 필요한 최소한의 정보만 안전하게 다룹니다.',
    sections: [
      {
        title: '수집하는 정보',
        paragraphs: [
          '로그인과 서비스 제공을 위해 필요한 계정 정보 및 서비스 이용 기록이 수집될 수 있습니다.',
        ],
      },
      {
        title: '이용 목적',
        paragraphs: [
          '수집한 정보는 로그인 처리, 관심종목 기능 제공, 서비스 개선 및 문의 응대에 사용됩니다.',
        ],
      },
      {
        title: '안내사항',
        paragraphs: ['본 페이지는 정식 개인정보처리방침 확정 전 제공되는 임시 안내 문구입니다.'],
      },
    ],
  },
  standard: {
    title: 'DAYNOMY Std.',
    lead: 'DAYNOMY가 지향하는 경제 정보 경험을 소개합니다.',
    sections: [
      {
        title: '읽기 쉬운 정보',
        paragraphs: [
          '경제 용어와 시장 흐름을 어렵지 않게 이해할 수 있도록 핵심 내용을 간결하게 전달합니다.',
        ],
      },
      {
        title: '균형 잡힌 시선',
        paragraphs: [
          '한쪽 방향의 결론보다 다양한 가능성과 확인해야 할 지점을 함께 보여드리는 것을 중요하게 생각합니다.',
        ],
      },
    ],
  },
};

export function InfoPage({ page }: { page: InfoPageType }) {
  const content = INFO_PAGE_CONTENT[page];

  return (
    <main className="info-page">
      <div className="info-page-content">
        <article className="info-card" aria-labelledby="info-page-title">
          <p className="info-eyebrow">DAYNOMY</p>
          <h1 id="info-page-title">{content.title}</h1>
          <p className="info-lead">{content.lead}</p>
          <div className="info-sections">
            {content.sections.map((section) => (
              <section key={section.title}>
                <h2>{section.title}</h2>
                {section.paragraphs.map((paragraph) => (
                  <p key={paragraph}>{paragraph}</p>
                ))}
              </section>
            ))}
          </div>
        </article>
      </div>
    </main>
  );
}
