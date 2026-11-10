/** @vitest-environment jsdom */
import { cleanup, fireEvent, render, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import LandingPage from '../../src/features/landing/LandingPage';
import { BrowserMockup } from '../../src/features/landing/BrowserMockup';

beforeEach(() => {
  vi.stubGlobal('matchMedia', () => ({
    matches: true,
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
  }));
  vi.stubGlobal(
    'ResizeObserver',
    class {
      observe() {}
      disconnect() {}
    },
  );
});
afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

it('배포된 제목과 네 장면을 유지하고 웹·모바일 이용을 안내한다', () => {
  const start = vi.fn();
  const view = render(
    <MemoryRouter>
      <LandingPage onStart={start} />
    </MemoryRouter>,
  );
  expect(view.getByRole('heading', { level: 1 }).textContent).toContain('내 투자에 미칠 영향');
  expect(view.getByText(/웹에서 넓게 관리하고/)).toBeTruthy();
  const controls = view.getByRole('group', { name: '소개 장면 선택' });
  expect(within(controls).getAllByRole('button')).toHaveLength(4);
  fireEvent.click(view.getByRole('link', { name: /웹에서 시작하기/ }));
  expect(start).toHaveBeenCalledOnce();
});

it('각 장면을 선택하면 해당 위치로 이동하고 스크롤과 목업이 함께 바뀐다', () => {
  const view = render(
    <MemoryRouter>
      <LandingPage onStart={vi.fn()} />
    </MemoryRouter>,
  );
  const root = view.container.querySelector<HTMLElement>('.welcome-film')!;
  const scroll = vi.fn();
  root.scrollTo = scroll;
  const controls = within(view.getByRole('group', { name: '소개 장면 선택' }));
  const scenes = ['portfolio', 'news', 'analysis', 'league'];
  controls.getAllByRole('button').forEach((button, index) => {
    fireEvent.click(button);
    expect(scroll).toHaveBeenLastCalledWith({ top: index * 180, behavior: 'instant' });
    root.scrollTop = index * 180;
    fireEvent.scroll(root);
    expect(root.dataset.scene).toBe(scenes[index]);
    expect(button.getAttribute('aria-current')).toBe('step');
    expect(view.container.querySelector('.film-browser-screen img')?.getAttribute('alt')).toContain(
      ['내 포트폴리오', '보유 자산 소식', 'AI 분석 · 근거', '주간 투자 리그'][index],
    );
    expect(view.container.querySelector('.film-device .guide-device')).toBeTruthy();
  });
});

it('동작 줄이기에서는 인트로를 생략하고 사용법·기술력·FAQ를 제공한다', () => {
  const view = render(
    <MemoryRouter>
      <LandingPage onStart={vi.fn()} />
    </MemoryRouter>,
  );
  expect(view.queryByRole('button', { name: '인트로 건너뛰기' })).toBeNull();
  expect(view.container.querySelectorAll('.welcome-guide-page')).toHaveLength(5);
  expect(view.container.querySelector('#technology')).toBeTruthy();
  expect(view.container.querySelectorAll('.welcome-faq details')).toHaveLength(3);
});

it('실제 캡처의 넘친 영역만 이동하고 동작 줄이기로 전환하면 정지한다', () => {
  const view = render(<BrowserMockup screen="overview" animate />);
  const image = view.getByRole('img');
  const viewport = image.parentElement!;
  viewport.style.padding = '10px';
  const cancel = vi.fn();
  const animate = vi.fn(() => ({ cancel }));
  Object.defineProperties(image, {
    clientHeight: { value: 1200 },
    complete: { value: true },
    animate: { value: animate },
  });
  Object.defineProperty(viewport, 'clientHeight', { value: 500 });
  fireEvent.load(image);
  expect(animate).toHaveBeenCalledWith(
    expect.arrayContaining([{ transform: 'translateY(-720px)', offset: 1 }]),
    expect.objectContaining({ duration: 12000 }),
  );
  view.rerender(<BrowserMockup screen="overview" animate={false} />);
  expect(cancel).toHaveBeenCalledOnce();
});

it('이어지는 휠 입력은 한 단계로 묶고 마지막 장면 이후에는 본문 스크롤을 허용한다', () => {
  let time = 1000;
  vi.spyOn(performance, 'now').mockImplementation(() => time);
  const view = render(
    <MemoryRouter>
      <LandingPage onStart={vi.fn()} />
    </MemoryRouter>,
  );
  const root = view.container.querySelector<HTMLElement>('.welcome-film')!;
  const scroll = vi.fn();
  root.scrollTo = scroll;
  const wheel = () => fireEvent.wheel(root, { deltaY: 500, cancelable: true });
  expect(wheel()).toBe(false);
  expect(scroll).toHaveBeenLastCalledWith({ top: 180, behavior: 'instant' });
  root.scrollTop = 180;
  fireEvent.scroll(root);
  time += 60;
  expect(wheel()).toBe(false);
  expect(scroll).toHaveBeenCalledTimes(1);
  time += 300;
  wheel();
  expect(scroll).toHaveBeenLastCalledWith({ top: 360, behavior: 'instant' });
  root.scrollTop = 360;
  fireEvent.scroll(root);
  time += 300;
  wheel();
  expect(scroll).toHaveBeenLastCalledWith({ top: 540, behavior: 'instant' });
  root.scrollTop = 540;
  fireEvent.scroll(root);
  time += 60;
  expect(wheel()).toBe(false);
  time += 300;
  expect(wheel()).toBe(true);
});
