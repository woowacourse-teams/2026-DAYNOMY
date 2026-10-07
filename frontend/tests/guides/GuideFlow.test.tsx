/** @vitest-environment jsdom */

import { cleanup, fireEvent, render } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, describe, expect, it } from 'vitest';
import { AccountOpeningGuidePage } from '../../src/features/guides/AccountOpeningGuidePage';
import { GuideDetailPage } from '../../src/features/guides/GuideDetailPage';
import { GuideListPage } from '../../src/features/guides/GuideListPage';
import { INDEX_CHOICE_STORAGE_KEY } from '../../src/features/guides/IndexChoiceGuide';
import { GUIDE_PROGRESS_STORAGE_KEY } from '../../src/features/guides/guideProgress';

afterEach(() => {
  cleanup();
  localStorage.clear();
});

describe('금융 가이드 흐름', () => {
  it('가이드 탭에서 계좌 개설 가이드와 다른 가이드를 함께 보여준다', () => {
    const view = render(
      <MemoryRouter>
        <GuideListPage />
      </MemoryRouter>,
    );

    expect(view.getByRole('heading', { name: '전체 가이드' })).toBeTruthy();
    expect(
      view.getByRole('link', { name: /증권계좌 개설부터 첫 납입까지/ }).getAttribute('href'),
    ).toBe('/guides/account-opening');
    expect(view.getByRole('link', { name: /ETF를 비교할 때 확인할 것/ })).toBeTruthy();
  });

  it('추천 배너를 넘기면 이미지와 연결 가이드가 함께 바뀐다', () => {
    const view = render(
      <MemoryRouter>
        <GuideListPage />
      </MemoryRouter>,
    );

    expect(view.getByRole('heading', { name: '계좌 개설, 어디서부터 눌러야 할까?' })).toBeTruthy();

    fireEvent.click(view.getByRole('button', { name: '다음 배너' }));

    const activeBannerHeading = view.getByRole('heading', {
      level: 1,
      name: '월 30만 원, 나스닥 vs S&P500 어디에 넣을까?',
    });
    expect(activeBannerHeading).toBeTruthy();
    expect(activeBannerHeading.closest('a')?.getAttribute('href')).toBe('/guides/index-choice');
  });

  it('계좌 개설 가이드의 단계를 완료하고 진행 상태를 저장한다', () => {
    const view = render(
      <MemoryRouter>
        <AccountOpeningGuidePage />
      </MemoryRouter>,
    );

    expect(view.getByText('주식 메뉴에서 내 계좌를 먼저 확인')).toBeTruthy();
    expect(view.getByAltText('주식 홈과 투자 내역이 표시된 토스 공식 앱 소개 화면')).toBeTruthy();

    fireEvent.click(view.getByRole('button', { name: '이 단계 완료' }));

    expect(view.container.querySelector('.account-guide-progress')?.textContent).toContain('1 / 8');
    expect(view.getByText('계좌 개설 전 본인 명의 계좌도 준비')).toBeTruthy();
    expect(
      view.getByAltText('은행 계좌와 증권 계좌가 함께 표시된 토스 공식 앱 소개 화면'),
    ).toBeTruthy();
    expect(localStorage.getItem(GUIDE_PROGRESS_STORAGE_KEY)).toContain('check-account');
  });

  it('저장된 진행 상태에서 가장 앞의 미완료 단계를 자동으로 펼친다', () => {
    localStorage.setItem(
      GUIDE_PROGRESS_STORAGE_KEY,
      JSON.stringify({ 'account-opening': ['check-account', 'prepare'] }),
    );

    const view = render(
      <MemoryRouter>
        <AccountOpeningGuidePage />
      </MemoryRouter>,
    );

    expect(
      view
        .getByRole('button', { name: /조건 확인계좌 종류와 비용을 먼저 비교하세요/ })
        .getAttribute('aria-expanded'),
    ).toBe('true');
    expect(
      view.getByText('혜택 문구보다 거래 가능한 상품과 수수료, 유지 조건을 확인합니다.'),
    ).toBeTruthy();
  });

  it('마지막 단계까지 완료하면 완료 안내와 다시 보기 기능을 제공한다', () => {
    const view = render(
      <MemoryRouter>
        <AccountOpeningGuidePage />
      </MemoryRouter>,
    );

    for (let index = 0; index < 7; index += 1) {
      fireEvent.click(view.getByRole('button', { name: '이 단계 완료' }));
    }
    fireEvent.click(view.getByRole('button', { name: /가이드 완료하기/ }));

    expect(view.getByRole('heading', { name: '첫 실행을 위한 준비가 끝났어요.' })).toBeTruthy();
    fireEvent.click(view.getByRole('button', { name: '처음부터 다시 보기' }));
    expect(view.container.querySelector('.account-guide-progress')?.textContent).toContain('0 / 8');
  });

  it('일반 가이드는 한 단계씩 펼치고 완료하면 다음 단계로 이동한다', () => {
    const view = render(
      <MemoryRouter initialEntries={['/guides/etf-basics']}>
        <Routes>
          <Route path="/guides/:guideId" element={<GuideDetailPage />} />
        </Routes>
      </MemoryRouter>,
    );

    expect(view.getByRole('heading', { name: 'ETF를 비교할 때 확인할 것' })).toBeTruthy();
    expect(
      view
        .getByRole('button', { name: /이름보다 종목코드와 상품 유형을 먼저 봐요/ })
        .getAttribute('aria-expanded'),
    ).toBe('true');

    for (let index = 0; index < 6; index += 1) {
      fireEvent.click(view.getByRole('button', { name: '이 단계 완료' }));
    }

    expect(view.getByRole('heading', { name: '이제 직접 적용해 볼 차례예요.' })).toBeTruthy();
    expect(view.getByRole('button', { name: '처음부터 다시 보기' })).toBeTruthy();
    expect(localStorage.getItem(GUIDE_PROGRESS_STORAGE_KEY)).toContain('completed');
  });

  it('비정기 지출을 항목별로 입력하면 매달 준비할 금액을 계산한다', () => {
    const view = render(
      <MemoryRouter initialEntries={['/guides/safety-money']}>
        <Routes>
          <Route path="/guides/:guideId" element={<GuideDetailPage />} />
        </Routes>
      </MemoryRouter>,
    );

    fireEvent.click(view.getByRole('button', { name: /가끔 나가지만 반드시 생기는 돈/ }));
    expect(view.getByRole('heading', { name: '지난 1년 내역, 이렇게 찾고 계산해요' })).toBeTruthy();

    fireEvent.change(view.getByLabelText('여행 연간 지출'), { target: { value: '120' } });
    fireEvent.change(view.getByLabelText('병원 연간 지출'), { target: { value: '60' } });

    expect(view.getByText('15만원')).toBeTruthy();
    expect(view.container.querySelector('.irregular-expense-result > p')?.textContent).toContain(
      '180만원',
    );
  });

  it('ETF 비교 가이드에서 실제 배분 예시와 포트폴리오 동선을 제공한다', () => {
    const view = render(
      <MemoryRouter initialEntries={['/guides/index-choice']}>
        <Routes>
          <Route path="/guides/:guideId" element={<GuideDetailPage />} />
        </Routes>
      </MemoryRouter>,
    );

    expect(view.getByRole('heading', { name: 'S&P500과 나스닥100, 뭘 사야 할까?' })).toBeTruthy();
    expect(view.getByRole('heading', { name: '처음이라면 S&P500을 중심으로' })).toBeTruthy();
    expect(view.getByText(/미국 대표 기업 약 500개를 여러 산업/)).toBeTruthy();
    expect(view.getByText(/기술주 중심 100개를 담아/)).toBeTruthy();
    expect(view.getByRole('heading', { name: '연간 수익률 비교' })).toBeTruthy();
    expect(
      view.getByRole('img', { name: /나스닥100과 S&P500 연간 가격 수익률 비교/ }),
    ).toBeTruthy();
    expect(view.getByText('큰 하락 사이의 간격은 6년, 14년으로 일정하지 않았습니다.')).toBeTruthy();
    expect(view.getByLabelText('S&P500 24만 원, 나스닥100 6만 원')).toBeTruthy();

    fireEvent.click(view.getByRole('button', { name: /성장 중심/ }));
    expect(view.getByLabelText('S&P500 6만 원, 나스닥100 24만 원')).toBeTruthy();
    fireEvent.click(view.getByRole('button', { name: '월 투자안 저장하기' }));

    expect(localStorage.getItem(INDEX_CHOICE_STORAGE_KEY)).toContain('"marketPercent":20');
    expect(localStorage.getItem(GUIDE_PROGRESS_STORAGE_KEY)).toContain('completed');
    expect(view.getByRole('link', { name: '포트폴리오 보기' }).getAttribute('href')).toBe('/');
  });
});
