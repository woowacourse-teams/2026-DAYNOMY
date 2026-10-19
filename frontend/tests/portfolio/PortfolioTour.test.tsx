/** @vitest-environment jsdom */
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { PortfolioTour } from '../../src/features/portfolio/components/PortfolioTour';

const originalShowModal = HTMLDialogElement.prototype.showModal;
const originalClose = HTMLDialogElement.prototype.close;
beforeEach(() => {
  localStorage.clear();
  HTMLDialogElement.prototype.showModal = function () {
    this.open = true;
  };
  HTMLDialogElement.prototype.close = function () {
    this.open = false;
  };
});
afterEach(() => {
  cleanup();
  vi.restoreAllMocks();
  localStorage.clear();
  HTMLDialogElement.prototype.showModal = originalShowModal;
  HTMLDialogElement.prototype.close = originalClose;
});

describe('포트폴리오 가이드 투어', () => {
  it('대상의 위치 이동과 DOM 교체를 따라가고 긴 영역의 테두리를 임의로 자르지 않는다', async () => {
    let top = 40;
    let height = 1200;
    const originalBounds = HTMLElement.prototype.getBoundingClientRect;
    vi.spyOn(HTMLElement.prototype, 'getBoundingClientRect').mockImplementation(function (
      this: HTMLElement,
    ) {
      return this.dataset.portfolioTour === 'trade'
        ? new DOMRect(30, top, 200, height)
        : originalBounds.call(this);
    });
    const view = render(
      <>
        <div key="first" data-portfolio-tour="trade" />
        <PortfolioTour ready />
      </>,
    );
    const highlight = () => document.querySelector<HTMLElement>('.portfolio-tour-highlight')!;
    await waitFor(() => expect(highlight().style.top).toBe('36px'));
    expect(highlight().style.height).toBe('1208px');
    top = -100;
    await waitFor(() => expect(highlight().style.top).toBe('-104px'));
    height = 80;
    top = 200;
    view.rerender(
      <>
        <div key="replacement" data-portfolio-tour="trade" />
        <PortfolioTour ready />
      </>,
    );
    await waitFor(() => expect(highlight().style.top).toBe('196px'));
    expect(highlight().style.height).toBe('88px');
  });

  it('다음으로 예시를 채우고 완료 후 수동으로 다시 연다', async () => {
    const onRegister = vi.fn();
    const onStepChange = vi.fn();
    const view = render(
      <PortfolioTour ready={false} onRegister={onRegister} onStepChange={onStepChange} />,
    );
    expect(screen.queryByRole('region', { name: '포트폴리오 사용 안내' })).toBeNull();
    view.rerender(<PortfolioTour ready onRegister={onRegister} onStepChange={onStepChange} />);
    await screen.findByRole('heading', { name: '예시로 자산 등록을 시작해요' });
    const next = () => fireEvent.click(screen.getByRole('button', { name: '다음' }));
    next();
    expect(onStepChange).toHaveBeenLastCalledWith(2);
    next();
    expect(onStepChange).toHaveBeenLastCalledWith(6);
    for (let i = 0; i < 7; i++) next();
    expect(onStepChange).toHaveBeenLastCalledWith(14);
    fireEvent.click(screen.getByRole('button', { name: '내 자산 등록하기' }));
    expect(onRegister).toHaveBeenCalledTimes(1);
    expect(screen.queryByRole('region', { name: '포트폴리오 사용 안내' })).toBeNull();
    view.unmount();
    render(<PortfolioTour ready />);
    expect(screen.queryByRole('region', { name: '포트폴리오 사용 안내' })).toBeNull();
    fireEvent.click(screen.getByRole('button', { name: '사용 가이드' }));
    await screen.findByRole('heading', { name: '예시로 자산 등록을 시작해요' });
  });
  it.each(['skip', 'close', 'escape'])(
    '%s로 종료하면 재방문 시 자동 노출하지 않는다',
    async (action) => {
      const { unmount } = render(<PortfolioTour ready />);
      await screen.findByRole('heading', { name: '예시로 자산 등록을 시작해요' });
      if (action === 'skip') fireEvent.click(screen.getByRole('button', { name: '종료' }));
      else if (action === 'close')
        fireEvent.click(screen.getByRole('button', { name: '가이드 닫기' }));
      else fireEvent.keyDown(document, { key: 'Escape' });
      expect(screen.queryByRole('region', { name: '포트폴리오 사용 안내' })).toBeNull();
      unmount();
      render(<PortfolioTour ready />);
      expect(screen.queryByRole('region', { name: '포트폴리오 사용 안내' })).toBeNull();
    },
  );
  it('저장소를 사용할 수 없어도 안내를 닫을 수 있다', async () => {
    vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
      throw new Error('blocked');
    });
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new Error('blocked');
    });
    render(<PortfolioTour ready />);
    await screen.findByRole('heading', { name: '예시로 자산 등록을 시작해요' });
    fireEvent.click(screen.getByRole('button', { name: '종료' }));
    expect(screen.queryByRole('region', { name: '포트폴리오 사용 안내' })).toBeNull();
  });
});

describe('비회원 첫 방문 말풍선', () => {
  it.each(['close', 'start', 'leave'])('%s 후 재방문하면 안내를 반복하지 않는다', (action) => {
    const view = render(<PortfolioTour ready isGuest />);
    expect(screen.getByRole('complementary', { name: '처음 이용 안내' })).toBeTruthy();
    expect(screen.queryByRole('region', { name: '포트폴리오 사용 안내' })).toBeNull();
    if (action === 'close') {
      fireEvent.click(screen.getByRole('button', { name: '처음 이용 안내 닫기' }));
      expect(document.activeElement).toBe(screen.getByRole('button', { name: '사용 가이드' }));
    } else if (action === 'start') {
      fireEvent.click(screen.getByRole('button', { name: '사용 가이드' }));
      expect(screen.getByRole('region', { name: '포트폴리오 사용 안내' })).toBeTruthy();
    }
    if (action !== 'leave') expect(screen.queryByRole('complementary')).toBeNull();
    view.unmount();
    render(<PortfolioTour ready isGuest />);
    expect(screen.queryByRole('complementary')).toBeNull();
    expect(screen.queryByRole('region', { name: '포트폴리오 사용 안내' })).toBeNull();
  });
  it('로딩 중에는 방문을 기록하지 않고 준비가 끝나면 표시한다', () => {
    const view = render(<PortfolioTour ready={false} isGuest />);
    expect(screen.queryByRole('complementary')).toBeNull();
    expect(localStorage.getItem('daynomy:portfolio-guide-seen:v1')).toBeNull();
    view.rerender(<PortfolioTour ready isGuest />);
    expect(screen.getByRole('complementary')).toBeTruthy();
  });
  it('회원이나 기존 가이드 이용자에게는 표시하지 않는다', () => {
    const view = render(<PortfolioTour ready />);
    expect(screen.queryByRole('complementary')).toBeNull();
    expect(localStorage.getItem('daynomy:portfolio-guide-seen:v1')).toBeNull();
    view.unmount();
    localStorage.setItem('daynomy:portfolio-tour:v1', 'done');
    render(<PortfolioTour ready isGuest />);
    expect(screen.queryByRole('complementary')).toBeNull();
  });
  it('저장소가 차단되어도 닫고 가이드를 시작할 수 있다', () => {
    vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
      throw new Error('blocked');
    });
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new Error('blocked');
    });
    render(<PortfolioTour ready isGuest />);
    fireEvent.click(screen.getByRole('button', { name: '처음 이용 안내 닫기' }));
    expect(screen.queryByRole('complementary')).toBeNull();
    fireEvent.click(screen.getByRole('button', { name: '사용 가이드' }));
    expect(screen.getByRole('region', { name: '포트폴리오 사용 안내' })).toBeTruthy();
  });
});
