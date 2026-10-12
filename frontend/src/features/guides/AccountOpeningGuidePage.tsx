import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { loadGuideProgress, saveGuideSteps } from './guideProgress';
import './guides.css';

const GUIDE_ID = 'account-opening';

type StepVisual = {
  src: string;
  alt: string;
  source: string;
  callout: string;
  position: 'top' | 'bottom';
};

type AccountGuideStep = {
  id: string;
  label: string;
  title: string;
  description: string;
  detail: string;
  caution?: string;
  visual: StepVisual;
};

const ACCOUNT_GUIDE_STEPS: AccountGuideStep[] = [
  {
    id: 'check-account',
    label: '계좌 확인',
    title: '이미 사용할 수 있는 계좌가 있는지 확인하세요',
    description: '새 계좌를 만들기 전에 현재 증권계좌에서 선택한 상품을 거래할 수 있는지 봅니다.',
    detail:
      '증권사 앱의 내 계좌 또는 계좌 관리에서 계좌 종류와 거래 가능 상품을 확인하세요. 이미 조건을 충족하는 계좌가 있다면 새로 만들 필요가 없습니다.',
    visual: {
      src: 'https://play-lh.googleusercontent.com/0dHBYr9-6-KJ4tEYJqKXRhyh8Sia91h4pNk14UaNMUH-Ffvz2WAkgXJIMZA38C9Qu92_Qmo_Cu9pCVa6bb63VQ=w1052-h592',
      alt: '주식 홈과 투자 내역이 표시된 토스 공식 앱 소개 화면',
      source: '토스 공식 Google Play 공개 화면 예시',
      callout: '주식 메뉴에서 내 계좌를 먼저 확인',
      position: 'top',
    },
  },
  {
    id: 'prepare',
    label: '준비물',
    title: '본인 명의 휴대폰과 신분증을 준비하세요',
    description: '비대면 계좌 개설에는 본인 확인을 위한 준비물이 필요합니다.',
    detail:
      '주민등록증이나 운전면허증, 본인 명의 휴대폰, 본인 명의 은행계좌를 가까이 두세요. 밝은 장소에서 신분증을 촬영하면 인식 오류를 줄일 수 있습니다.',
    caution: '신분증이나 계좌번호가 보이는 화면을 캡처하거나 다른 사람에게 전송하지 마세요.',
    visual: {
      src: 'https://play-lh.googleusercontent.com/0cO36oeW8gxPEVa0XwUEPUk61nxOnZ3-ZMbFpxvwCdn6gi8NkX7oRO5Fjd8LHFaRG2Eh6OWj8WSSSJPzfkpnd88=w1052-h592',
      alt: '은행 계좌와 증권 계좌가 함께 표시된 토스 공식 앱 소개 화면',
      source: '토스 공식 Google Play 공개 화면 예시',
      callout: '계좌 개설 전 본인 명의 계좌도 준비',
      position: 'bottom',
    },
  },
  {
    id: 'compare-terms',
    label: '조건 확인',
    title: '계좌 종류와 비용을 먼저 비교하세요',
    description: '혜택 문구보다 거래 가능한 상품과 수수료, 유지 조건을 확인합니다.',
    detail:
      '일반 위탁계좌인지, ISA나 연금계좌인지 확인하세요. 거래 수수료, 환전 비용, 이벤트 종료 후 적용되는 조건도 함께 읽어야 합니다.',
    visual: {
      src: 'https://play-lh.googleusercontent.com/0dHBYr9-6-KJ4tEYJqKXRhyh8Sia91h4pNk14UaNMUH-Ffvz2WAkgXJIMZA38C9Qu92_Qmo_Cu9pCVa6bb63VQ=w1052-h592',
      alt: '주식 홈과 투자 내역이 표시된 토스 공식 앱 소개 화면',
      source: '토스 공식 Google Play 공개 화면 예시',
      callout: '내 계좌에서 계좌 종류와 거래 조건 확인',
      position: 'top',
    },
  },
  {
    id: 'verify',
    label: '본인 확인',
    title: '앱 안내에 따라 본인 확인을 완료하세요',
    description: '약관 동의, 휴대폰 인증, 신분증 촬영과 본인 계좌 확인을 진행합니다.',
    detail:
      '필수 약관과 선택 약관을 구분해서 확인하세요. 인증이 반복해서 실패하면 입력한 이름과 신분증 정보가 일치하는지 확인합니다.',
    caution: '비밀번호, 인증번호, 보안카드는 누구에게도 알려주면 안 됩니다.',
    visual: {
      src: 'https://play-lh.googleusercontent.com/0cO36oeW8gxPEVa0XwUEPUk61nxOnZ3-ZMbFpxvwCdn6gi8NkX7oRO5Fjd8LHFaRG2Eh6OWj8WSSSJPzfkpnd88=w1052-h592',
      alt: '은행 계좌와 증권 계좌가 함께 표시된 토스 공식 앱 소개 화면',
      source: '토스 공식 Google Play 공개 화면 예시',
      callout: '토스 앱 안내에 따라 본인 확인 진행',
      position: 'top',
    },
  },
  {
    id: 'confirm-opened',
    label: '개설 확인',
    title: '계좌번호와 거래 가능 상태를 확인하세요',
    description: '계좌가 목록에 표시되고 입금 가능한 상태인지 확인합니다.',
    detail:
      '계좌 관리 화면에서 새 계좌의 이름과 번호 끝자리, 거래 가능 상품을 확인하세요. 해외 상품은 별도 거래 신청이 필요할 수 있습니다.',
    visual: {
      src: 'https://play-lh.googleusercontent.com/0cO36oeW8gxPEVa0XwUEPUk61nxOnZ3-ZMbFpxvwCdn6gi8NkX7oRO5Fjd8LHFaRG2Eh6OWj8WSSSJPzfkpnd88=w1052-h592',
      alt: '은행 계좌와 증권 계좌가 함께 표시된 토스 공식 앱 소개 화면',
      source: '토스 공식 Google Play 공개 화면 예시',
      callout: '새 계좌와 주문 가능 금액이 보이는지 확인',
      position: 'bottom',
    },
  },
  {
    id: 'first-deposit',
    label: '첫 입금',
    title: '작은 금액을 먼저 입금해 보세요',
    description: '큰 금액을 보내기 전에 입금 계좌와 반영 여부를 확인합니다.',
    detail:
      '본인 은행 앱에서 새 증권계좌로 소액을 이체한 뒤 증권사 앱의 주문 가능 금액에 반영됐는지 확인하세요. 계좌번호와 예금주를 이체 전에 다시 봅니다.',
    visual: {
      src: 'https://play-lh.googleusercontent.com/0cO36oeW8gxPEVa0XwUEPUk61nxOnZ3-ZMbFpxvwCdn6gi8NkX7oRO5Fjd8LHFaRG2Eh6OWj8WSSSJPzfkpnd88=w1052-h592',
      alt: '은행 계좌와 송금 메뉴가 표시된 토스 공식 앱 소개 화면',
      source: '토스 공식 Google Play 공개 화면 예시',
      callout: '송금에서 새 계좌로 소액 먼저 이체',
      position: 'bottom',
    },
  },
  {
    id: 'automatic-transfer',
    label: '자동이체',
    title: '유지 가능한 금액으로 자동이체를 설정하세요',
    description: '월급일 이후처럼 잔액을 확보할 수 있는 날짜를 선택합니다.',
    detail:
      '출금 계좌, 입금 계좌, 이체 금액, 시작일과 종료일을 확인하세요. 처음에는 생활비에 부담이 없는 금액으로 설정하고 다음 달 정상 이체 여부를 확인합니다.',
    caution:
      '자동이체 등록은 자동매수와 다릅니다. 상품 매수가 필요한 경우 별도 주문 설정을 확인하세요.',
    visual: {
      src: 'https://play-lh.googleusercontent.com/YhJqKtcnonLBT1NKFDfHGTPDKb-MhW0bS5ifD5WYA7Wc3Hc9ur4PBLJpEoVp1GHJV7ODolC93KZOYXhLsSVEyw=w1052-h592',
      alt: '토스뱅크 계좌와 이자 내역이 표시된 토스 공식 앱 소개 화면',
      source: '토스 공식 Google Play 공개 화면 예시',
      callout: '계좌 관리에서 자동이체 날짜와 금액 설정',
      position: 'bottom',
    },
  },
  {
    id: 'final-check',
    label: '완료 확인',
    title: '첫 실행에 필요한 설정을 마지막으로 점검하세요',
    description: '계좌 개설, 첫 입금, 자동이체가 의도한 대로 설정됐는지 확인합니다.',
    detail:
      '계좌번호 끝자리, 첫 입금 반영, 다음 자동이체 날짜와 금액을 확인하세요. 앱 알림을 켜두면 이체 실패나 주문 상태를 놓치지 않는 데 도움이 됩니다.',
    visual: {
      src: 'https://play-lh.googleusercontent.com/0dHBYr9-6-KJ4tEYJqKXRhyh8Sia91h4pNk14UaNMUH-Ffvz2WAkgXJIMZA38C9Qu92_Qmo_Cu9pCVa6bb63VQ=w1052-h592',
      alt: '주식 계좌의 잔액과 투자 내역이 표시된 토스 공식 앱 소개 화면',
      source: '토스 공식 Google Play 공개 화면 예시',
      callout: '주문 가능 금액과 계좌 상태를 마지막으로 확인',
      position: 'top',
    },
  },
];

function initialCompletedSteps() {
  const savedSteps = loadGuideProgress()[GUIDE_ID] ?? [];
  return savedSteps.filter((stepId) => ACCOUNT_GUIDE_STEPS.some((step) => step.id === stepId));
}

function firstIncompleteStepId(completedSteps: string[]) {
  return (
    ACCOUNT_GUIDE_STEPS.find((step) => !completedSteps.includes(step.id))?.id ??
    ACCOUNT_GUIDE_STEPS.at(-1)!.id
  );
}

export function AccountOpeningGuidePage() {
  const [completedSteps, setCompletedSteps] = useState<string[]>(initialCompletedSteps);
  const [activeStepId, setActiveStepId] = useState(() =>
    firstIncompleteStepId(initialCompletedSteps()),
  );
  const [showHelp, setShowHelp] = useState(false);
  const isFinished = completedSteps.length === ACCOUNT_GUIDE_STEPS.length;
  const progress = (completedSteps.length / ACCOUNT_GUIDE_STEPS.length) * 100;
  const nextIncompleteStep = ACCOUNT_GUIDE_STEPS.find((step) => !completedSteps.includes(step.id));

  useEffect(() => {
    saveGuideSteps(GUIDE_ID, completedSteps);
  }, [completedSteps]);

  function completeStep(stepId: string, stepIndex: number) {
    const nextCompletedSteps = completedSteps.includes(stepId)
      ? completedSteps
      : [...completedSteps, stepId];
    setCompletedSteps(nextCompletedSteps);
    setShowHelp(false);

    const nextStep = ACCOUNT_GUIDE_STEPS.slice(stepIndex + 1).find(
      (step) => !nextCompletedSteps.includes(step.id),
    );
    setActiveStepId(nextStep?.id ?? firstIncompleteStepId(nextCompletedSteps));
  }

  function selectStep(stepId: string) {
    setActiveStepId(stepId);
    setShowHelp(false);
  }

  function resetGuide() {
    setCompletedSteps([]);
    setActiveStepId(ACCOUNT_GUIDE_STEPS[0].id);
    setShowHelp(false);
  }

  return (
    <main className="guide-page account-guide-page">
      <nav className="guide-breadcrumb" aria-label="현재 위치">
        <Link to="/guides">가이드</Link>
        <span aria-hidden="true">/</span>
        <strong>계좌 개설부터 첫 납입까지</strong>
      </nav>

      <header className="account-guide-header">
        <div>
          <h1>
            계좌 개설부터 첫 납입까지,
            <br />
            하나씩 확인하세요.
          </h1>
        </div>
        <p>
          완료한 단계는 접어두고, 지금 해야 할 단계만 펼쳐 보여드려요. 화면 예시는 앱 버전과 계좌
          상태에 따라 다를 수 있습니다.
        </p>
      </header>

      <section className="account-guide-progress" aria-labelledby="account-guide-progress-title">
        <div>
          <span id="account-guide-progress-title">전체 진행률</span>
          <strong>
            {completedSteps.length}
            <small> / {ACCOUNT_GUIDE_STEPS.length}단계</small>
          </strong>
        </div>
        <div className="account-guide-progress-track" aria-hidden="true">
          <i style={{ width: `${progress}%` }} />
        </div>
        <p>
          {isFinished ? '모든 단계를 확인했습니다.' : `다음 할 일 · ${nextIncompleteStep?.label}`}
        </p>
      </section>

      <section className="account-guide-accordion" aria-label="계좌 개설 실행 단계">
        <ol>
          {ACCOUNT_GUIDE_STEPS.map((step, index) => {
            const isComplete = completedSteps.includes(step.id);
            const isActive = activeStepId === step.id;
            const panelId = `account-guide-panel-${step.id}`;

            return (
              <li
                className={`${isActive ? 'is-active' : ''}${isComplete ? ' is-complete' : ''}`}
                key={step.id}
              >
                <button
                  type="button"
                  className="account-guide-accordion-trigger"
                  aria-expanded={isActive}
                  aria-controls={panelId}
                  onClick={() => selectStep(step.id)}
                >
                  <span className="account-guide-check" aria-hidden="true">
                    {isComplete ? null : index + 1}
                  </span>
                  <span className="account-guide-accordion-title">
                    <small>{step.label}</small>
                    <strong>{step.title}</strong>
                  </span>
                  <span className="account-guide-state">
                    {isComplete ? '완료' : isActive ? '진행 중' : '확인 전'}
                  </span>
                  <span className="account-guide-chevron" aria-hidden="true" />
                </button>

                {isActive ? (
                  <div className="account-guide-accordion-panel" id={panelId}>
                    <div className="account-guide-panel-copy">
                      <p className="account-guide-lead">{step.description}</p>
                      <section className="account-guide-detail">
                        <span>지금 확인할 것</span>
                        <p>{step.detail}</p>
                      </section>

                      {step.caution ? (
                        <aside className="account-guide-caution">
                          <strong>주의</strong>
                          <p>{step.caution}</p>
                        </aside>
                      ) : null}

                      {showHelp ? (
                        <aside className="account-guide-help" aria-live="polite">
                          <strong>화면이 안내와 다른가요?</strong>
                          <p>
                            앱의 검색 또는 전체 메뉴에서 ‘계좌’, ‘이체’, ‘자동이체’를 검색하세요.
                            찾기 어렵다면 금융회사 공식 고객센터의 최신 안내를 확인하세요.
                          </p>
                        </aside>
                      ) : null}
                    </div>

                    <figure className="account-guide-capture">
                      <div>
                        <img src={step.visual.src} alt={step.visual.alt} />
                        <span className={`is-${step.visual.position}`}>{step.visual.callout}</span>
                      </div>
                      <figcaption>{step.visual.source}</figcaption>
                    </figure>

                    <footer className="account-guide-actions">
                      <button type="button" onClick={() => setShowHelp((current) => !current)}>
                        화면이 달라요
                      </button>
                      <button type="button" onClick={() => completeStep(step.id, index)}>
                        {isComplete
                          ? '다음 미완료 단계로 이동'
                          : index === ACCOUNT_GUIDE_STEPS.length - 1
                            ? '가이드 완료하기'
                            : '이 단계 완료'}
                      </button>
                    </footer>
                  </div>
                ) : null}
              </li>
            );
          })}
        </ol>
      </section>

      {isFinished ? (
        <section className="account-guide-finished" aria-live="polite">
          <div>
            <span>모든 단계 완료</span>
            <h2>첫 실행을 위한 준비가 끝났어요.</h2>
            <p>다음 자동이체 날짜와 금액은 금융회사 앱에서 한 번 더 확인해 주세요.</p>
          </div>
          <div>
            <button type="button" onClick={resetGuide}>
              처음부터 다시 보기
            </button>
            <Link to="/guides">다른 가이드 보기 →</Link>
          </div>
        </section>
      ) : null}
    </main>
  );
}
