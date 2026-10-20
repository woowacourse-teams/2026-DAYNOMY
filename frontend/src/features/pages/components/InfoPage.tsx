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
          '경제 기사, 인물의 글과 YouTube·Threads·X 자료를 통합 검색하고, 뉴스 상세의 키워드와 시장 분석을 통해 필요한 정보를 확인할 수 있습니다.',
        ],
      },
      {
        title: '포트폴리오 기능',
        paragraphs: [
          '국내 주식·ETF의 매수·매도 거래와 보유 정보를 기록하고, 수집된 종가 기준 평가금액·손익·자산 구성과 보유 자산 관련 소식을 확인할 수 있습니다.',
          '회원의 포트폴리오·거래 기록·분석 결과는 계정에 연결해 서버에 저장하며 자산 추이를 제공합니다. 비회원의 보유 정보·거래 기록·분석 체험 결과는 현재 브라우저 탭의 세션 저장소에 임시 보관합니다. 최근 검색어는 브라우저 저장소에서 직접 삭제할 수 있습니다.',
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
          'DAYNOMY는 경제 뉴스·인물 글·외부 자료의 탐색과 통합 검색, 뉴스 상세 분석, 국내 주식·ETF의 포트폴리오·거래 기록과 AI 분석 기능을 제공합니다.',
          '뉴스와 시장 데이터의 제공 시점, 정확성, 누락 여부는 원천 데이터와 운영 상황에 따라 달라질 수 있습니다.',
        ],
      },
      {
        title: '제2조 서비스 이용',
        paragraphs: [
          '일반 이용자는 로그인 없이 공개 소식·통합 검색과 임시 포트폴리오·거래 기록을 이용하고 AI 분석을 1회 체험할 수 있습니다. 계정에 연결한 기록 저장, 자산 추이와 회원 분석 조회·갱신은 로그인 후 이용합니다. 공개 투자 리그와 프로필은 로그인 없이 조회할 수 있으며, 리그 참여·종목 관리·판단·복기 작성·팔로우는 로그인 후 이용합니다.',
          '서비스를 이용하는 과정에서 관계 법령과 서비스 화면의 안내를 따라야 하며, 다른 이용자나 서비스의 정상적인 이용을 방해해서는 안 됩니다.',
        ],
      },
      {
        title: '제3조 포트폴리오와 투자 정보',
        paragraphs: [
          '포트폴리오 결과는 사용자가 입력한 보유·거래 정보와 수집된 종가를 기준으로 계산한 참고용 결과입니다. 시세 기준일과 누락 여부는 화면의 안내를 확인해 주세요.',
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
    lead: 'DAYNOMY는 회원 인증과 서비스 운영에 필요한 범위에서 개인정보를 처리합니다.',
    sections: [
      {
        title: '수집하는 정보',
        paragraphs: [
          '일반 이용자는 로그인 없이 공개 화면을 이용할 수 있으며, 공개 화면 이용만을 위해 회원가입을 요구하지 않습니다.',
          'Google OAuth 로그인 과정에서는 회원 계정의 식별자, 이메일 주소, 이름, 프로필 이미지 주소를 처리할 수 있습니다. 회원의 포트폴리오 보유 정보·거래 기록과 분석 결과는 계정에 연결해 서버에 저장합니다.',
          '인증을 위해 브라우저 쿠키가 사용될 수 있습니다. Google Analytics 4가 설정된 환경에서는 방문·이벤트 분석에 브라우저 쿠키가 사용될 수 있습니다. PostHog 분석과 콘텐츠 인기순 중복 집계를 위해 각각 브라우저에서 생성한 익명 식별자를 localStorage에 저장할 수 있습니다. 최근 검색어는 localStorage에, 비회원의 보유 정보·거래 기록·분석 체험 결과는 현재 탭의 sessionStorage에 저장하며 비회원 임시 기록을 로그인한 계정으로 자동 이전하지 않습니다.',
        ],
      },
      {
        title: '이용 목적',
        paragraphs: [
          '수집한 정보는 Google 로그인 처리, 회원 식별, 닉네임 관리, 인증 토큰 발급·갱신·무효화, 계정별 포트폴리오·거래 기록·분석 결과 제공, 보안 및 문의 응대에 사용됩니다.',
          'Sentry가 설정된 환경에서는 서비스 오류를 확인할 수 있습니다. Sentry 전송 데이터에서는 기본 사용자 정보와 요청의 쿠키·본문·헤더·쿼리 문자열 필드가 제거되고, 요청 및 breadcrumb URL의 쿼리와 fragment가 제거됩니다.',
        ],
      },
      {
        title: '서비스 이용 분석',
        paragraphs: [
          'Google Analytics 4와 PostHog가 설정된 환경에서는 서비스 이용 흐름을 파악하고 기능과 사용성을 개선하기 위해 방문 경로와 유입 경로, 콘텐츠 노출·선택·필터·페이지 이동, 검색어 길이, 포트폴리오 화면 조작·가이드 이용·저장 결과, 분석 요청·결과·출처 확인, 피드백 열기·제출 성공·실패를 분석합니다.',
          '콘텐츠 인기순 제공을 위해 콘텐츠 종류와 식별자, 열기·원문 클릭 반응, 반응 일자를 DAYNOMY 서버에 저장합니다. 브라우저의 익명 식별자는 동일한 날짜의 중복 반응을 판별하는 해시를 만드는 데만 사용하며, 원본 식별자나 회원 계정 정보와 연결해 서버에 저장하지 않습니다.',
          'PostHog에는 이벤트 이름과 발생 시각, 브라우저에서 생성한 방문·기기·세션·탭 식별자, 브라우저·운영체제와 버전, 기기 유형, 화면·브라우저 표시 영역 크기, 기능 이용 상태와 성공·실패 등의 정보가 전송됩니다. 로그인 여부나 보유 자산 유무·개수 등의 상태를 포함할 수 있으나 회원 계정 식별자·이메일·이름을 분석 식별자로 연결하거나 개인 프로필을 생성하지 않습니다.',
          '검색어 원문, 피드백 본문, 보유 종목 목록·금액·수량과 관리자·로그인 화면 이벤트는 분석 도구에 전송하지 않습니다. 방문 주소와 유입 주소의 쿼리 문자열·fragment를 제거하고 투자자 프로필 주소의 공개 식별자를 일반 경로로 바꿉니다. PostHog의 자동 클릭 수집과 세션 녹화는 사용하지 않으며, Google Ads와 연동하지 않습니다.',
        ],
      },
      {
        title: 'PostHog 외부 분석 서비스',
        paragraphs: [
          '서비스 이용 분석에는 PostHog Inc.의 PostHog Cloud US 서비스를 이용합니다. PostHog가 설정된 환경에서 위 분석 정보는 페이지 조회나 기능 이용 시 네트워크를 통해 미국의 PostHog 서버로 전송되어 분석·보관됩니다. 분석 요청의 네트워크 통신 과정에서 IP 주소가 PostHog에 전달됩니다.',
          'PostHog의 개인정보 관련 연락처는 privacy@posthog.com입니다. DAYNOMY에서 수집하는 분석 정보에 대한 처리 중지·삭제 요청은 아래 DAYNOMY 문의 주소로 접수해 주세요.',
        ],
      },
      {
        title: '보관과 삭제',
        paragraphs: [
          '회원 계정의 이용이 종료되면 인증 토큰을 삭제하고 계정을 탈퇴 상태로 전환합니다. 브라우저의 최근 검색어와 비회원 임시 기록은 브라우저 저장소에서 삭제할 수 있습니다. 비회원 임시 기록은 현재 탭의 세션에 보관되며 브라우저 저장소 삭제가 회원의 서버 기록 삭제를 의미하지는 않습니다.',
          'Google Analytics 4의 사용자 및 이벤트 단위 데이터 보관 기간은 2개월로 설정되어 있습니다. 집계 보고서에는 별도의 보관 기준이 적용될 수 있습니다.',
          'PostHog 분석 이벤트에는 현재 이용 중인 무료 플랜의 1년 보관 정책이 적용됩니다. 이 기간은 이벤트 조회·보관 정책이며 기간이 지나면 즉시 모든 데이터가 삭제된다는 의미는 아닙니다. 실제 삭제에는 PostHog의 삭제 정책과 처리 절차가 적용됩니다.',
          'PostHog 방문 식별자는 브라우저 저장소를 삭제하거나 브라우저가 데이터를 정리할 때까지 남아 있을 수 있습니다. 브라우저 저장소 삭제는 이미 PostHog 서버에 전송된 분석 정보의 삭제나 이후 분석 수집의 거부를 의미하지 않으며, 이후 방문 시 새로운 식별자가 생성될 수 있습니다.',
          '콘텐츠 인기순용 익명 식별자는 브라우저 저장소를 삭제할 때까지 남아 있을 수 있으며, 삭제 후에는 새로운 식별자가 생성될 수 있습니다. 서버에는 원본 식별자 대신 콘텐츠·반응·일자별 중복 판별 해시가 저장됩니다.',
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
                    으로 Google Analytics 분석을 거부할 수 있습니다. 이 부가기능은 PostHog 분석
                    수집을 차단하지 않습니다.
                  </p>
                  <p>
                    PostHog 분석 요청을 차단하는 브라우저의 콘텐츠 차단 기능이나 확장 기능을 통해
                    전송을 차단할 수 있습니다. 차단은 설정한 브라우저와 기기에 적용되며, 이미 전송된
                    정보를 삭제하지 않습니다. 처리 중지·삭제 관련 요청은{' '}
                    <a href="mailto:paperchoigo@gmail.com">paperchoigo@gmail.com</a>으로 접수해
                    주세요.
                  </p>
                  <p>
                    외부 분석 서비스의 정책은{' '}
                    <a href="https://posthog.com/privacy">PostHog 개인정보처리방침</a>과{' '}
                    <a href="https://posthog.com/docs/data/events-retention">
                      PostHog 이벤트 보관 정책
                    </a>
                    에서 확인할 수 있습니다.
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
