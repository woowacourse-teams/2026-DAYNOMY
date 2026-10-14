/** @vitest-environment jsdom */

import { act, cleanup, fireEvent, render } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { KeywordText } from '../../src/features/news/newsdetail/components/KeywordText';
import type { KeywordResponse } from '../../src/features/news/newsdetail/types';

import { trackEvent } from '../../src/analytics';
vi.mock('../../src/analytics', () => ({ trackEvent: vi.fn() }));

const keyword: KeywordResponse = {
  keyword: '외환시장',
  category: 'TERM',
  definition: '서로 다른 통화를 사고파는 시장이에요.',
  context: '외국인 투자자의 거래 편의성과 연결돼요.',
  watchPoint: '제도 개선의 시행 일정을 확인하세요.',
};

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
  vi.useRealTimers();
  vi.restoreAllMocks();
});

describe('키워드 팝업', () => {
  it('클릭 없이 열고 팝업으로 이동하면 유지하며 영역을 벗어나면 닫는다', () => {
    vi.useFakeTimers();
    const view = render(<KeywordText paragraphs={['외환시장 제도 개선']} keywords={[keyword]} />);
    const trigger = view.getByRole('button', { name: '외환시장' });
    expect(view.queryByRole('tooltip')).toBeNull();
    fireEvent.pointerEnter(trigger);
    const tooltip = view.getByRole('tooltip');
    expect(tooltip.textContent).toContain(keyword.context);
    fireEvent.pointerLeave(trigger);
    act(() => {
      vi.advanceTimersByTime(100);
    });
    fireEvent.pointerEnter(tooltip);
    act(() => {
      vi.advanceTimersByTime(200);
    });
    expect(view.getByRole('tooltip')).toBe(tooltip);
    fireEvent.pointerLeave(tooltip);
    act(() => {
      vi.advanceTimersByTime(200);
    });
    expect(view.queryByRole('tooltip')).toBeNull();
  });

  it('키보드 포커스로 설명을 열고 Escape 또는 포커스 이동으로 닫는다', () => {
    const view = render(<KeywordText paragraphs={['외환시장 제도 개선']} keywords={[keyword]} />);
    const trigger = view.getByRole('button', { name: '외환시장' });
    fireEvent.focus(trigger);
    expect(trigger.getAttribute('aria-describedby')).toBe(view.getByRole('tooltip').id);
    fireEvent.keyDown(trigger, { key: 'Escape' });
    expect(view.queryByRole('tooltip')).toBeNull();
    fireEvent.focus(trigger);
    fireEvent.blur(trigger, { relatedTarget: document.body });
    expect(view.queryByRole('tooltip')).toBeNull();
  });

  it('터치 클릭으로 열고 팝업 내부 터치는 유지하며 바깥 터치로 닫는다', () => {
    const view = render(<KeywordText paragraphs={['외환시장 제도 개선']} keywords={[keyword]} />);
    fireEvent.click(view.getByRole('button', { name: '외환시장' }));
    fireEvent.pointerDown(view.getByRole('tooltip'), { pointerType: 'touch' });
    fireEvent.blur(view.getByRole('button', { name: '외환시장' }), { relatedTarget: null });
    expect(view.getByRole('tooltip')).toBeTruthy();
    fireEvent.pointerDown(document.body, { pointerType: 'touch' });
    expect(view.queryByRole('tooltip')).toBeNull();
  });

  it('긴 키워드와 빈 목록에서도 본문과 문단 순서를 보존한다', () => {
    const longKeyword = '외국인투자자의국내외환시장거래접근성개선';
    const paragraphs = ['첫 번째 문단이에요.', `${longKeyword} 내용을 확인하세요.`];
    const view = render(
      <KeywordText paragraphs={paragraphs} keywords={[{ ...keyword, keyword: longKeyword }]} />,
    );
    expect(view.getByRole('button', { name: longKeyword })).toBeTruthy();
    expect(Array.from(view.container.querySelectorAll('p'), (p) => p.textContent)).toEqual(
      paragraphs,
    );
    view.rerender(<KeywordText paragraphs={paragraphs} keywords={[]} />);
    expect(view.queryByRole('button')).toBeNull();
    expect(Array.from(view.container.querySelectorAll('p'), (p) => p.textContent)).toEqual(
      paragraphs,
    );
  });
});

it('같은 설명의 hover·focus·click은 한 번만 집계하고 닫은 뒤 다시 열면 집계한다', () => {
  const view = render(<KeywordText newsId="7" paragraphs={['외환시장']} keywords={[keyword]} />);
  const button = view.getByRole('button');
  fireEvent.pointerEnter(button);
  fireEvent.focus(button);
  fireEvent.click(button);
  expect(trackEvent).toHaveBeenCalledTimes(1);
  expect(trackEvent).toHaveBeenLastCalledWith('open_keyword_analysis', {
    news_id: '7',
    keyword_category: 'TERM',
    trigger: 'hover',
  });
  fireEvent.keyDown(button, { key: 'Escape' });
  fireEvent.click(button);
  expect(trackEvent).toHaveBeenCalledTimes(2);
  expect(trackEvent).toHaveBeenLastCalledWith('open_keyword_analysis', {
    news_id: '7',
    keyword_category: 'TERM',
    trigger: 'click',
  });
  expect(JSON.stringify(vi.mocked(trackEvent).mock.calls)).not.toContain(keyword.keyword);
});
