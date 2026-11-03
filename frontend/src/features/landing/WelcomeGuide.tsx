import { GuideMockup } from './GuideMockup';

const guideSteps = [
  {
    id: 'guide',
    label: '거래 기록',
    title: '내 거래를,',
    emphasis: '그대로 기록하세요.',
    screen: 'trade',
    instructions: [
      '「종목 추가」에서 기업명이나 종목 코드를 검색해요.',
      '매수·매도를 선택하고 거래일, 수량, 단가를 입력해요.',
      '수수료·세금을 확인하고 거래를 저장해요.',
    ],
    note: '비회원 기록은 현재 브라우저 탭에만 보관돼요.',
  },
  {
    id: 'guide-overview',
    label: '자산 확인',
    title: '내 자산과 손익을,',
    emphasis: '한눈에 확인하세요.',
    screen: 'overview',
    instructions: [
      '전체 평가금액과 보유 자산의 손익을 확인해요.',
      '보유 종목별 비중과 수익률을 비교해요.',
      '로그인하면 자산 추이와 누적 실현손익도 볼 수 있어요.',
    ],
    note: '평가금액은 서비스가 제공하는 최근 종가를 기준으로 계산해요.',
  },
  {
    id: 'guide-news',
    label: '관련 소식',
    title: '내 종목의 소식만,',
    emphasis: '모아 살펴보세요.',
    screen: 'news',
    instructions: [
      '포트폴리오에서 「소식」을 열어요.',
      '종목과 출처를 골라 관심 있는 자료를 찾아요.',
      '영상을 펼치거나 원문을 열어 내용을 확인해요.',
    ],
    note: '뉴스·YouTube·인물 콘텐츠를 한곳에서 볼 수 있어요.',
  },
  {
    id: 'guide-analysis',
    label: 'AI 분석',
    title: '내 자산에 미칠 영향,',
    emphasis: '근거까지 확인하세요.',
    screen: 'analysis',
    instructions: [
      '보유 종목을 등록한 뒤 「AI 분석하기」를 눌러요.',
      '종목별 영향과 보유 비중을 반영한 분석을 읽어요.',
      '분석에 연결된 출처를 열어 판단의 근거를 확인해요.',
    ],
    note: '비회원도 1회 체험할 수 있어요. 분석은 투자 참고 정보예요.',
  },
  {
    id: 'guide-league',
    label: '투자 리그',
    title: '순위 너머,',
    emphasis: '투자자의 생각까지.',
    screen: 'league',
    instructions: [
      '투자 리그에서 주차와 리그를 선택해 순위를 살펴봐요.',
      '공개 프로필을 열어 보유 이유와 투자 복기를 읽어요.',
      '직접 참여하려면 로그인 후 「투자리그 참여하기」를 눌러요.',
    ],
    note: '잠정 순위와 확정 순위를 구분해 확인하세요.',
  },
] as const;

export function WelcomeGuide() {
  return (
    <div className="welcome-guide">
      {guideSteps.map((step, index) => (
        <section
          key={step.id}
          id={step.id}
          className="welcome-guide-page"
          aria-labelledby={`${step.id}-title`}
        >
          <div className="welcome-guide-layout">
            <div className="welcome-guide-reading">
              <header className="welcome-guide-heading">
                <span>
                  사용법 {String(index + 1).padStart(2, '0')} / 05 · {step.label}
                </span>
                <h2 id={`${step.id}-title`} tabIndex={-1}>
                  {step.title}
                  <br />
                  <em>{step.emphasis}</em>
                </h2>
              </header>
              <div className="welcome-guide-instructions">
                <ol>
                  {step.instructions.map((instruction) => (
                    <li key={instruction}>{instruction}</li>
                  ))}
                </ol>
                <p>{step.note}</p>
              </div>
            </div>
            <div className="welcome-guide-device">
              <GuideMockup screen={step.screen} label={`사용법: ${step.label}`} />
            </div>
            <nav className="welcome-guide-navigation" aria-label={`${step.label} 사용법 이동`}>
              <a href={index === 0 ? '#home' : `#${guideSteps[index - 1].id}`}>
                ↑ {index === 0 ? '소개로 돌아가기' : '이전 단계'}
              </a>
              <a
                href={
                  index === guideSteps.length - 1 ? '#technology' : `#${guideSteps[index + 1].id}`
                }
              >
                {index === guideSteps.length - 1
                  ? '기술력 살펴보기'
                  : `다음: ${guideSteps[index + 1].label}`}{' '}
                ↓
              </a>
            </nav>
          </div>
        </section>
      ))}
    </div>
  );
}
