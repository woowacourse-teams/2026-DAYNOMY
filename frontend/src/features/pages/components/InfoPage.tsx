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
    lead: 'DAYNOMY는 경제 뉴스와 포트폴리오를 한곳에서 살펴보는 정보 서비스입니다.',
    sections: [
      {
        title: '서비스 소개',
        paragraphs: [
          '오늘의 주요 경제 뉴스와 시장 흐름을 카테고리별로 정리해 제공합니다.',
          '뉴스 검색, 뉴스 상세의 키워드와 시장 분석을 통해 필요한 정보를 빠르게 확인할 수 있습니다.',
        ],
      },
      {
        title: '포트폴리오 기능',
        paragraphs: [
          '국내 주식의 보유 수량과 평균 매수가를 입력하면 전일 종가 기준 평가금액, 수익률, 시장별 비중을 계산합니다.',
          '입력한 포트폴리오와 최근 검색어는 현재 브라우저의 저장소에 보관되며, 사용자가 브라우저에서 직접 삭제할 수 있습니다.',
        ],
      },
      {
        title: '정보 이용 안내',
        paragraphs: [
          'DAYNOMY의 뉴스, 시세, 분석 결과는 정보 확인을 위한 참고 자료입니다. 특정 금융상품의 매수·매도나 투자 결과를 보장하지 않습니다.',
        ],
      },
    ],
  },
  terms: {
    title: '이용약관',
    lead: 'DAYNOMY의 뉴스·검색·포트폴리오 기능을 이용하기 전에 확인해야 할 기준입니다.',
    sections: [
      {
        title: '제1조 서비스의 내용',
        paragraphs: [
          'DAYNOMY는 경제 뉴스 목록·검색·상세 분석과 국내 주식 포트폴리오 계산 기능을 제공합니다.',
          '뉴스와 시장 데이터의 제공 시점, 정확성, 누락 여부는 원천 데이터와 운영 상황에 따라 달라질 수 있습니다.',
        ],
      },
      {
        title: '제2조 서비스 이용',
        paragraphs: [
          '일반 이용자는 별도의 회원가입이나 로그인 없이 DAYNOMY의 공개 뉴스, 검색, 포트폴리오 기능을 이용할 수 있습니다.',
          '서비스를 이용하는 과정에서 관계 법령과 서비스 화면의 안내를 따라야 하며, 다른 이용자나 서비스의 정상적인 이용을 방해해서는 안 됩니다.',
        ],
      },
      {
        title: '제3조 포트폴리오와 투자 정보',
        paragraphs: [
          '포트폴리오 결과는 사용자가 입력한 보유 정보와 서비스가 제공하는 전일 종가를 기준으로 계산한 참고용 결과입니다.',
          'DAYNOMY는 투자 자문이나 금융상품의 매매 권유를 제공하지 않습니다. 투자 판단과 그 결과에 대한 책임은 이용자에게 있습니다.',
        ],
      },
      {
        title: '제4조 콘텐츠와 서비스 변경',
        paragraphs: [
          'DAYNOMY가 직접 작성한 화면 구성, 문구, 소프트웨어와 브랜드 요소의 권리는 DAYNOMY 또는 정당한 권리자에게 있습니다. 뉴스 원문·이미지·외부 데이터의 권리는 각 권리자에게 있습니다.',
          '서비스 품질 개선, 데이터 제공처 변경, 보안 또는 운영상의 이유로 기능과 화면은 변경되거나 일시 중단될 수 있습니다.',
        ],
      },
      {
        title: '제5조 문의',
        paragraphs: ['서비스 이용과 관련한 문의는 푸터의 문의하기 주소로 접수할 수 있습니다.'],
      },
    ],
  },
  privacy: {
    title: '개인정보처리방침',
    lead: 'DAYNOMY는 관리자 인증과 서비스 운영에 필요한 범위에서 개인정보를 처리합니다.',
    sections: [
      {
        title: '수집하는 정보',
        paragraphs: [
          '일반 이용자는 로그인 없이 공개 화면을 이용할 수 있으며, 공개 화면 이용만을 위해 회원가입을 요구하지 않습니다.',
          '서비스 운영을 위한 관리자 인증 과정에서는 Google OAuth를 통해 관리자 계정의 식별자, 이메일 주소, 이름, 프로필 이미지 주소를 처리할 수 있습니다.',
          '인증을 위해 브라우저 쿠키가 사용될 수 있습니다. Google Analytics 4가 설정된 환경에서는 방문·이벤트 분석에 브라우저 쿠키가 사용될 수 있습니다. 포트폴리오 보유 정보와 최근 검색어는 현재 브라우저의 localStorage에 저장되며 서버 회원 정보와 별도로 관리됩니다.',
        ],
      },
      {
        title: '이용 목적',
        paragraphs: [
          '수집한 정보는 Google 로그인 처리, 회원 식별, 닉네임 관리, 인증 토큰 발급·갱신·무효화, 보안 및 문의 응대에 사용됩니다.',
          'Google Analytics 4로 공개 화면의 방문 경로, 뉴스 카테고리·기사 번호, 검색어 길이를 분석합니다. 검색어 원문과 관리자 로그인 이벤트는 전송하지 않으며, Google Ads와 연동하지 않습니다.',
          'Sentry가 설정된 환경에서는 서비스 오류를 확인할 수 있습니다. Sentry 전송 데이터에서는 기본 사용자 정보와 요청의 쿠키·본문·헤더·쿼리 문자열 필드가 제거되고, 요청 및 breadcrumb URL의 쿼리와 fragment가 제거됩니다.',
        ],
      },
      {
        title: '보관과 삭제',
        paragraphs: [
          '관리자 계정의 이용이 종료되면 인증 토큰을 삭제하고 계정을 탈퇴 상태로 전환합니다. 브라우저에 저장된 포트폴리오와 최근 검색어는 이용자가 브라우저 저장소를 직접 삭제해야 합니다.',
          'Google Analytics 4의 사용자 및 이벤트 단위 데이터 보관 기간은 2개월로 설정되어 있습니다. 집계 보고서에는 별도의 보관 기준이 적용될 수 있습니다.',
          '법령상 보관이 필요한 정보가 있거나 분쟁 대응을 위해 필요한 경우에는 해당 목적에 필요한 기간 동안 보관할 수 있습니다.',
        ],
      },
      {
        title: '이용자의 권리와 문의',
        paragraphs: [
          '이용자는 자신의 개인정보에 대해 조회, 수정, 삭제 또는 처리 중지를 요청할 수 있습니다. 서비스에서 직접 처리하기 어려운 요청은 문의하기 주소로 접수해 주세요.',
          '개인정보 처리와 관련한 문의는 paperchoigo@gmail.com으로 보내주시면 확인 후 답변드리겠습니다.',
        ],
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
          <header className="info-header">
            <h1 id="info-page-title">{content.title}</h1>
            <p className="info-lead">{content.lead}</p>
          </header>
          <div className="info-sections">
            {content.sections.map((section) => (
              <section key={section.title}>
                <h2>{section.title}</h2>
                <div className="info-section-copy">
                  {section.paragraphs.map((paragraph) => (
                    <p key={paragraph}>{paragraph}</p>
                  ))}
                </div>
              </section>
            ))}
            {page === 'privacy' ? (
              <section>
                <h2>이용 분석 거부</h2>
                <div className="info-section-copy">
                  <p>
                    지원하는 브라우저에서는{' '}
                    <a href="https://tools.google.com/dlpage/gaoptout?hl=ko">
                      Google Analytics 차단 브라우저 부가기능
                    </a>
                    으로 분석을 거부할 수 있습니다.
                  </p>
                </div>
              </section>
            ) : null}
          </div>
        </article>
      </div>
    </main>
  );
}
