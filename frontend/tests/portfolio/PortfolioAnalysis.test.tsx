/** @vitest-environment jsdom */

import { act, cleanup, fireEvent, render, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { PortfolioAnalysis } from '../../src/features/portfolio/components/PortfolioAnalysis';
import {
  GUEST_PORTFOLIO_ANALYSIS_STORAGE_KEY,
  GUEST_PORTFOLIO_ANALYSIS_TRIAL_STORAGE_KEY,
  PORTFOLIO_ANALYSIS_STORAGE_KEY,
} from '../../src/features/portfolio/portfolioAnalysisStorage';

import { trackEvent } from '../../src/analytics';
vi.mock('../../src/analytics', () => ({ trackEvent: vi.fn() }));

function jsonResponse(body: unknown, status = 200) {
  if (body && typeof body === 'object' && 'overallScore' in body) {
    body = {
      taskId: 'test-task',
      status: 'READY',
      analysis: body,
      summaryStatus: 'READY',
      failureCode: null,
      retryAllowed: false,
    };
  }
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'content-type': 'application/json' },
  });
}

const assets = [
  { assetName: '삼성전자', weight: 60 },
  { assetName: 'SK하이닉스', weight: 40 },
];

const analysisResponse = {
  totalAssetCount: 2,
  analyzedAssetCount: 2,
  overallDirection: 'POSITIVE',
  overallScore: 51,
  positiveImpactScore: 60,
  negativeImpactScore: 9,
  analyzedAt: '2026-10-05T08:00:00Z',
  overallImpact: '반도체 업황 개선 기대가 포트폴리오에 긍정적으로 작용할 수 있어요.',
  impacts: [
    {
      assetName: '삼성전자',
      weight: 60,
      direction: 'POSITIVE',
      impactLevel: 'HIGH',
      issueSummary: '반도체 수요가 증가했어요.',
      expectedReaction: '실적 개선 기대가 주가에 긍정적으로 반영될 수 있어요.',
      outlook: '수요 회복 흐름을 확인해야 해요.',
      reason: '보유 비중이 높아 포트폴리오에 미치는 영향도 커요.',
      evidenceSentence: '반도체 수요가 전년보다 증가했어요.',
      sources: [{ title: '반도체 산업 동향', url: 'https://example.com/semiconductor' }],
      rank: 1,
    },
    {
      assetName: 'SK하이닉스',
      weight: 40,
      direction: 'NEGATIVE',
      impactLevel: 'LOW',
      issueSummary: '메모리 가격 변동성이 커졌어요.',
      expectedReaction: '단기 주가 변동성이 커질 수 있어요.',
      outlook: '가격 안정 여부를 지켜봐야 해요.',
      reason: '부정 영향은 예상되지만 영향 수준은 낮아요.',
      evidenceSentence: '메모리 현물 가격 변동성이 확대됐어요.',
      sources: [{ title: '메모리 시장 동향', url: 'https://example.com/memory' }],
      rank: 2,
    },
  ],
  sources: [
    { title: '반도체 산업 동향', url: 'https://example.com/semiconductor' },
    { title: '메모리 시장 동향', url: 'https://example.com/memory' },
  ],
};

beforeEach(() => {
  localStorage.clear();
  sessionStorage.clear();
});

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
  vi.restoreAllMocks();
  vi.unstubAllGlobals();
});

describe('포트폴리오 분석 화면', () => {
  it('상위 5종목 밖의 보유 종목을 근거 부족과 구분하고 전체 점수를 확정하지 않는다', async () => {
    const holdings = [30, 25, 20, 12, 8, 5].map((weight, index) => ({
      assetName: `종목${index + 1}`,
      weight,
    }));
    memberFetch(() =>
      saved({
        analysis: {
          ...analysisResponse,
          totalAssetCount: 6,
          analyzedAssetCount: 5,
          overallDirection: null,
          overallScore: null,
          excludedAssets: [holdings[5]],
          impacts: holdings
            .slice(0, 5)
            .map((asset, index) => ({ ...analysisResponse.impacts[0], ...asset, rank: index + 1 })),
        },
      }),
    );
    const view = render(<PortfolioAnalysis assets={holdings} />);
    await view.findByText('종목6');
    expect(view.getByText('분석 대상 제외')).toBeTruthy();
    expect(view.queryByText('근거 부족')).toBeNull();
    expect(view.getByText(/전체 포트폴리오 점수는 확정하지 않아요/)).toBeTruthy();
    expect(view.queryByText(/0점이에요/)).toBeNull();
  });

  it('비회원이 새로고침해도 기존 서버 작업을 조회하고 핵심 결과와 종합 설명을 차례로 받는다', async () => {
    let task = {
      ...emptyTask,
      taskId: 'task-1',
      status: 'PROCESSING',
      analysis: null as typeof analysisResponse | null,
      summaryStatus: null as string | null,
    };
    let started = false;
    const fetchMock = vi.fn(async (_input: RequestInfo | URL, init?: RequestInit) => {
      if (init?.method === 'POST') started = true;
      return jsonResponse(started ? task : emptyTask);
    });
    vi.stubGlobal('fetch', fetchMock);
    const first = render(<PortfolioAnalysis assets={assets} isLoggedIn={false} />);
    fireEvent.click(await first.findByRole('button', { name: '1회 분석 체험하기' }));
    await first.findByText(/다른 화면을 둘러보거나 새로고침해도/);
    first.unmount();
    const second = render(<PortfolioAnalysis assets={assets} isLoggedIn={false} />);
    await second.findByText(/다른 화면을 둘러보거나 새로고침해도/);
    task = {
      ...task,
      status: 'READY',
      analysis: { ...analysisResponse, overallImpact: '' },
      summaryStatus: 'PENDING',
    };
    await second.findByText(
      '점수와 종목별 근거가 준비됐어요. 종합 설명을 작성하고 있어요.',
      {},
      { timeout: 4500 },
    );
    expect(second.getByRole('heading', { name: '전체 포트폴리오' })).toBeTruthy();
    task = { ...task, analysis: analysisResponse, summaryStatus: 'READY' };
    await second.findByText(analysisResponse.overallImpact, {}, { timeout: 4500 });
    expect(fetchMock.mock.calls.filter(([, init]) => init?.method === 'POST')).toHaveLength(1);
    expect(
      fetchMock.mock.calls.every(([url]) => String(url).endsWith('/api/portfolio/analysis/tasks')),
    ).toBe(true);
  }, 12000);

  it.each([true, false])(
    '0점이 상쇄인지 중립인지 계산 이유를 표시한다 (상쇄: %s)',
    async (offset) => {
      memberFetch(() =>
        saved({
          analysis: {
            ...analysisResponse,
            overallDirection: 'NEUTRAL',
            overallScore: 0,
            positiveImpactScore: offset ? 30 : 0,
            negativeImpactScore: offset ? 30 : 0,
          },
        }),
      );
      const view = render(<PortfolioAnalysis assets={assets} />);
      expect(
        await view.findByText(
          offset
            ? /긍정 기여 \+30.00점과 부정 기여 −30.00점이 상쇄/
            : /영향이 모두 중립으로 평가돼/,
        ),
      ).toBeTruthy();
    },
  );

  it('비회원의 최초 분석 결과를 세션에 저장하고 이후 로그인을 안내한다', async () => {
    const fetchMock = vi.fn(async () => jsonResponse(analysisResponse));
    stubTaskFetch(fetchMock);
    const firstView = render(<PortfolioAnalysis assets={assets} isLoggedIn={false} />);
    expect(await firstView.findByRole('region', { name: '가상 분석 미리보기' })).toBeTruthy();
    expect(sessionStorage.getItem(GUEST_PORTFOLIO_ANALYSIS_STORAGE_KEY)).toBeNull();

    fireEvent.click(await firstView.findByRole('button', { name: '1회 분석 체험하기' }));

    expect(await firstView.findByRole('heading', { name: '전체 포트폴리오' })).toBeTruthy();
    expect(firstView.queryByRole('region', { name: '가상 분석 미리보기' })).toBeNull();
    expect(
      firstView.getByRole('link', { name: '로그인하고 다시 분석하기' }).getAttribute('href'),
    ).toBe('/login?returnTo=%2F');
    expect(sessionStorage.getItem(GUEST_PORTFOLIO_ANALYSIS_STORAGE_KEY)).toContain(
      analysisResponse.overallImpact,
    );
    expect(sessionStorage.getItem(GUEST_PORTFOLIO_ANALYSIS_TRIAL_STORAGE_KEY)).toBe('used');
    expect(localStorage.getItem(PORTFOLIO_ANALYSIS_STORAGE_KEY)).toBeNull();
    firstView.unmount();

    const secondView = render(<PortfolioAnalysis assets={assets} isLoggedIn={false} />);
    expect(await secondView.findByRole('heading', { name: '전체 포트폴리오' })).toBeTruthy();
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it('비회원 분석 실패 시 체험 완료로 기록하지 않고 재시도를 허용한다', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(jsonResponse({}, 500))
      .mockResolvedValueOnce(jsonResponse(analysisResponse));
    stubTaskFetch(fetchMock);
    const view = render(<PortfolioAnalysis assets={assets} isLoggedIn={false} />);

    fireEvent.click(await view.findByRole('button', { name: '1회 분석 체험하기' }));
    expect(await view.findByRole('alert')).toBeTruthy();
    expect(sessionStorage.getItem(GUEST_PORTFOLIO_ANALYSIS_TRIAL_STORAGE_KEY)).toBeNull();

    fireEvent.click(view.getByRole('button', { name: '다시 시도' }));
    expect(await view.findByRole('heading', { name: '전체 포트폴리오' })).toBeTruthy();
    expect(fetchMock).toHaveBeenCalledTimes(2);
  });

  it('백엔드가 비회원 체험 소진을 반환하면 로그인 전환을 안내한다', async () => {
    stubTaskFetch(
      vi.fn(async () =>
        jsonResponse(
          {
            code: 'PORTFOLIO_ANALYSIS_TRIAL_EXHAUSTED',
            message:
              '비회원 포트폴리오 분석 체험을 이미 사용했습니다. 로그인 후 다시 이용해주세요.',
          },
          403,
        ),
      ),
    );
    const view = render(<PortfolioAnalysis assets={assets} isLoggedIn={false} />);

    fireEvent.click(await view.findByRole('button', { name: '1회 분석 체험하기' }));

    expect(await view.findByText('무료 분석 체험을 모두 사용했어요.')).toBeTruthy();
    expect(view.getByRole('link', { name: '로그인하고 다시 분석하기' })).toBeTruthy();
    expect(sessionStorage.getItem(GUEST_PORTFOLIO_ANALYSIS_TRIAL_STORAGE_KEY)).toBe('used');
  });

  it('전체 보유 자산으로 분석하고 자산별 결과와 출처를 선택해 표시한다', async () => {
    const fetchMock = vi.fn(async (_input: RequestInfo | URL, _init?: RequestInit) =>
      jsonResponse(analysisResponse),
    );
    stubTaskFetch(fetchMock);
    const view = render(<PortfolioAnalysis assets={assets} isLoggedIn={false} />);

    fireEvent.click(await view.findByRole('button', { name: '1회 분석 체험하기' }));

    expect(await view.findByRole('heading', { name: '전체 포트폴리오' })).toBeTruthy();
    expect(view.getByText(analysisResponse.overallImpact)).toBeTruthy();
    expect(view.getByText(/분석 기준/)).toBeTruthy();

    const overallDonut = view.getByRole('img', {
      name: '전체 포트폴리오, 긍정 영향',
    });
    expect(overallDonut.getAttribute('fill')).toBe('#ef464d');
    const overallButton = view.getByRole('button', { name: '전체 포트폴리오 분석 보기' });
    expect(overallButton.textContent).toBe('전체 포트폴리오전체 분석');
    const overallListButton = view.getByRole('button', {
      name: '전체 포트폴리오 분석 보기, 긍정 영향, 영향 점수 +51.00점',
    });
    expect(view.getByText('영향 점수 +51.00점')).toBeTruthy();

    fireEvent.click(view.getByText('삼성전자').closest('button') as HTMLButtonElement);
    expect(view.getByRole('heading', { name: '삼성전자' })).toBeTruthy();
    expect(view.getByText('실적 개선 기대가 주가에 긍정적으로 반영될 수 있어요.')).toBeTruthy();
    expect(view.getByRole('button', { name: '전체 포트폴리오 분석 보기' }).textContent).toBe(
      '삼성전자60%',
    );
    const samsungDonutSegment = view.getByRole('button', {
      name: '삼성전자, 보유 비중 60%, 긍정 영향',
    });
    const hynixDonutSegment = view.getByRole('button', {
      name: 'SK하이닉스, 보유 비중 40%, 부정 영향',
    });
    expect(samsungDonutSegment.getAttribute('fill')).toBe('#ef464d');
    expect(hynixDonutSegment.getAttribute('fill')).toBe('#e8eef8');

    const request = fetchMock.mock.calls[0];
    expect(String(request[0])).toContain('/api/portfolio/analysis');
    expect(request[1]).toEqual(
      expect.objectContaining({
        method: 'POST',
        body: JSON.stringify({ assets }),
      }),
    );

    fireEvent.click(view.getByText('SK하이닉스').closest('button') as HTMLButtonElement);
    expect(view.getByRole('heading', { name: 'SK하이닉스' })).toBeTruthy();
    expect(view.getByText('단기 주가 변동성이 커질 수 있어요.')).toBeTruthy();

    const evidenceSummary = view.getByText('분석 출처 모두 보기');
    fireEvent.click(evidenceSummary);
    const sourceLink = view.getByRole('link', { name: '메모리 시장 동향' });
    expect(sourceLink.getAttribute('href')).toBe('https://example.com/memory');
    expect(sourceLink.getAttribute('target')).toBe('_blank');

    fireEvent.click(overallListButton);
    expect(view.getByRole('heading', { name: '전체 포트폴리오' })).toBeTruthy();
    expect(view.getByRole('img', { name: '전체 포트폴리오, 긍정 영향' }).getAttribute('fill')).toBe(
      '#ef464d',
    );
  });

  it('전체·종목 출처는 중복 링크와 설명 없이 제목·날짜만 표시한다', async () => {
    const source = {
      title: '기업 실적 발표',
      url: 'https://example.com/results',
      publishedOn: '2026-10-06',
      occurredOn: '2026-10-05',
      excerpt: '매출 증가를 발표했어요.',
    };
    const sources = [source, { ...source, excerpt: '중복 출처의 다른 설명이에요.' }];
    const response = {
      ...analysisResponse,
      overallImpact:
        '새 실적이 발표됐어요.[1]\n\n이익 개선을 기대해요. 규모는 미확정이에요.\n\n다음 발표를 확인해요.',
      sources,
      impacts: analysisResponse.impacts.map((impact) => ({ ...impact, sources })),
    };
    stubTaskFetch(vi.fn(async () => jsonResponse(response)));
    const view = render(<PortfolioAnalysis assets={assets} isLoggedIn={false} />);
    fireEvent.click(await view.findByRole('button', { name: '1회 분석 체험하기' }));
    await view.findByRole('heading', { name: '전체 포트폴리오' });
    const copy = view.container.querySelector('.portfolio-detail-copy')!;
    expect(copy.querySelectorAll('p')).toHaveLength(3);
    expect(copy.querySelector('a')).toBeNull();
    expect(copy.textContent).not.toContain('[1]');
    expect(copy.querySelector('strong')).toBeNull();
    const list = view.getByRole('list', { name: '포트폴리오 보유 자산' });
    expect(list.textContent).not.toMatch(/%|긍정|중립|부정/);
    fireEvent.click(view.getByText('분석 출처 모두 보기'));
    expect(view.getByRole('link', { name: '기업 실적 발표' }).getAttribute('href')).toBe(
      source.url,
    );
    expect(view.getByText('2026-10-06')).toBeTruthy();
    expect(view.getByText('2026-10-05')).toBeTruthy();
    expect(view.getAllByRole('link', { name: source.title })).toHaveLength(1);
    expect(view.queryByText(source.excerpt)).toBeNull();
    expect(view.queryByText(sources[1].excerpt)).toBeNull();
    fireEvent.click(view.getByText('삼성전자').closest('button')!);
    expect(view.container.querySelectorAll('.portfolio-detail-copy p')).toHaveLength(3);
    fireEvent.click(view.getByText('분석 출처 모두 보기'));
    expect(view.getAllByRole('link', { name: source.title })).toHaveLength(1);
    expect(view.queryByText(source.excerpt)).toBeNull();
    expect(view.queryByText(sources[1].excerpt)).toBeNull();
  });

  it('가중 영향 점수의 산정 기준과 기여 점수를 안내한다', async () => {
    stubTaskFetch(vi.fn(async () => jsonResponse(analysisResponse)));
    const view = render(<PortfolioAnalysis assets={assets} isLoggedIn={false} />);

    fireEvent.click(await view.findByRole('button', { name: '1회 분석 체험하기' }));
    const guideTitle = await view.findByText('영향 점수 산정 기준');
    const guide = guideTitle.closest('details')!;
    const toggle = guideTitle.closest('summary')!;
    expect(guide.open).toBe(false);
    fireEvent.click(toggle);
    expect(guide.open).toBe(true);

    expect(view.getByText(/예상 수익률이나 상승 확률은 아니에요/)).toBeTruthy();
    expect(view.getByText('+60.00점')).toBeTruthy();
    expect(view.getByText('−9.00점')).toBeTruthy();
    fireEvent.click(toggle);
    expect(guide.open).toBe(false);
  });

  it('비회원이 모든 자산을 삭제하면 저장된 분석 결과만 제거한다', async () => {
    sessionStorage.setItem(GUEST_PORTFOLIO_ANALYSIS_STORAGE_KEY, 'cached-analysis');
    sessionStorage.setItem(GUEST_PORTFOLIO_ANALYSIS_TRIAL_STORAGE_KEY, 'used');

    render(<PortfolioAnalysis assets={[]} isLoggedIn={false} />);

    await waitFor(() => {
      expect(sessionStorage.getItem(GUEST_PORTFOLIO_ANALYSIS_STORAGE_KEY)).toBeNull();
    });
    expect(sessionStorage.getItem(GUEST_PORTFOLIO_ANALYSIS_TRIAL_STORAGE_KEY)).toBe('used');
  });

  it.each([false, true])(
    '자산이 없으면 추가를 안내하고 분석 요청을 막는다 (로그인: %s)',
    async (isLoggedIn) => {
      const fetchMock = isLoggedIn
        ? memberFetch(() => saved({ status: 'EMPTY', analysis: null }))
        : vi.fn();
      stubTaskFetch(fetchMock);
      const onAddAsset = vi.fn();
      const view = render(
        <PortfolioAnalysis assets={[]} isLoggedIn={isLoggedIn} onAddAsset={onAddAsset} />,
      );
      expect(view.getByRole('region', { name: '가상 분석 미리보기' })).toBeTruthy();
      expect(
        view.container.querySelector('.portfolio-preview-layout .portfolio-analysis-donut'),
      ).toBeTruthy();
      expect(view.container.querySelector('.portfolio-preview-layout [inert]')).toBeTruthy();
      expect(
        view.container.querySelector('.portfolio-preview-blur')?.getAttribute('aria-hidden'),
      ).toBe('true');
      expect(view.queryByRole('button', { name: '1회 분석 체험하기' })).toBeNull();
      expect(view.queryByRole('button', { name: 'AI 분석하기' })).toBeNull();
      fireEvent.click(view.getByRole('button', { name: '자산 추가하기' }));
      expect(onAddAsset).toHaveBeenCalledTimes(1);
      if (!isLoggedIn) expect(fetchMock).not.toHaveBeenCalled();
      view.rerender(
        <PortfolioAnalysis assets={assets} isLoggedIn={isLoggedIn} onAddAsset={onAddAsset} />,
      );
      expect(
        await view.findByRole('button', {
          name: isLoggedIn ? 'AI 분석하기' : '1회 분석 체험하기',
        }),
      ).toBeTruthy();
      expect(view.queryByRole('button', { name: '자산 추가하기' })).toBeNull();
      const analyzeButton = view.getByRole('button', {
        name: isLoggedIn ? 'AI 분석하기' : '1회 분석 체험하기',
      });
      expect(analyzeButton.closest('.portfolio-preview-action')).toBeTruthy();
      expect(analyzeButton.classList.contains('is-primary')).toBe(true);
      expect(view.getByRole('region', { name: '가상 분석 미리보기' }).contains(analyzeButton)).toBe(
        true,
      );
    },
  );

  it('회원은 과거 localStorage 대신 서버 결과를 재접속할 때마다 복원한다', async () => {
    localStorage.setItem(
      PORTFOLIO_ANALYSIS_STORAGE_KEY,
      JSON.stringify({ analysis: { ...analysisResponse, overallImpact: '다른 사용자의 캐시' } }),
    );
    const fetchMock = memberFetch(() => saved());
    const first = render(<PortfolioAnalysis assets={assets} />);
    expect(await first.findByText(analysisResponse.overallImpact)).toBeTruthy();
    expect(first.queryByText('다른 사용자의 캐시')).toBeNull();
    expect(localStorage.getItem(PORTFOLIO_ANALYSIS_STORAGE_KEY)).toBeNull();
    first.unmount();
    const second = render(<PortfolioAnalysis assets={assets} />);
    expect(await second.findByText(analysisResponse.overallImpact)).toBeTruthy();
    expect(fetchMock.mock.calls.filter(([url]) => String(url).endsWith('/analysis'))).toHaveLength(
      2,
    );
    expect(fetchMock.mock.calls.every(([, init]) => !init?.method)).toBe(true);
  });

  it('새 소식 확인 중에는 안내만 표시하고 변경이 없으면 기존 결과를 다시 표시한다', async () => {
    let resolveRefresh!: (response: Response) => void;
    const fetchMock = memberFetch((url) =>
      url.endsWith('/refresh')
        ? new Promise<Response>((resolve) => {
            resolveRefresh = resolve;
          })
        : saved(),
    );
    const view = render(<PortfolioAnalysis assets={assets} />);
    fireEvent.click(await view.findByRole('button', { name: '새 소식 확인' }));
    await waitFor(() => expect(resolveRefresh).toBeDefined());
    expect(view.queryByText(analysisResponse.overallImpact)).toBeNull();
    expect(view.queryByRole('heading', { name: '자산별 영향' })).toBeNull();
    expect(view.getByText(/분석에 약 30초 정도 걸려요/)).toBeTruthy();
    const button = view.getByRole('button', { name: '확인 중' }) as HTMLButtonElement;
    expect(button.disabled).toBe(true);
    fireEvent.click(button);
    await act(async () =>
      resolveRefresh(
        jsonResponse(
          saved({ lastCheckOutcome: 'UNCHANGED', lastSucceededAt: '2026-10-06T08:00:00Z' }),
        ),
      ),
    );
    expect(await view.findByRole('button', { name: '새 소식 확인' })).toBeTruthy();
    expect(view.queryByText('주요 변화가 없어 기존 분석을 유지했어요.')).toBeNull();
    expect(view.queryByText(/보유 자산 \d+개 중 \d+개 분석/)).toBeNull();
    const checkedAt = view.getByText(/최근 확인:/).querySelector('time');
    expect(checkedAt?.getAttribute('datetime')).toBe('2026-10-06T08:00:00Z');
    expect(view.getByText(analysisResponse.overallImpact)).toBeTruthy();
    expect(fetchMock.mock.calls.filter(([url]) => String(url).endsWith('/refresh'))).toHaveLength(
      1,
    );
  });

  it('회원 검색 실패에도 기존 결과를 보존하고 재시도로 갱신 이유를 표시한다', async () => {
    let attempts = 0;
    memberFetch((url) => {
      if (!url.endsWith('/refresh')) return saved();
      if (attempts++ === 0) return jsonResponse({}, 502);
      return saved({
        changeReason: '새 계약 발표를 반영했어요.',
        analysis: { ...analysisResponse, overallImpact: '새 계약의 영향을 반영했어요.' },
      });
    });
    const view = render(<PortfolioAnalysis assets={assets} />);
    fireEvent.click(await view.findByRole('button', { name: '새 소식 확인' }));
    expect(await view.findByRole('alert')).toBeTruthy();
    expect(view.getByText(analysisResponse.overallImpact)).toBeTruthy();
    fireEvent.click(view.getByRole('button', { name: '다시 시도' }));
    expect(await view.findByText('새 계약의 영향을 반영했어요.')).toBeTruthy();
    expect(view.getByText('새 계약 발표를 반영했어요.')).toBeTruthy();
    expect(localStorage.getItem(PORTFOLIO_ANALYSIS_STORAGE_KEY)).toBeNull();
  });

  it('저장 결과 조회 실패의 재시도는 분석 생성 없이 다시 조회한다', async () => {
    let attempts = 0;
    const fetchMock = memberFetch(() => (attempts++ === 0 ? jsonResponse({}, 500) : saved()));
    const view = render(<PortfolioAnalysis assets={assets} />);
    expect(await view.findByRole('alert')).toBeTruthy();
    fireEvent.click(view.getByRole('button', { name: '다시 시도' }));
    expect(await view.findByText(analysisResponse.overallImpact)).toBeTruthy();
    expect(fetchMock.mock.calls.every(([, init]) => !init?.method)).toBe(true);
  });

  it('계정이 바뀌면 이전 결과와 늦게 도착한 응답을 노출하지 않는다', async () => {
    let resolveOld!: (response: Response) => void;
    let count = 0;
    const fetchMock = memberFetch(() =>
      count++ === 0
        ? new Promise<Response>((resolve) => {
            resolveOld = resolve;
          })
        : saved({ analysis: { ...analysisResponse, overallImpact: '두 번째 회원의 분석이에요.' } }),
    );
    const view = render(<PortfolioAnalysis memberId={1} assets={assets} />);
    view.rerender(<PortfolioAnalysis memberId={2} assets={assets} />);
    expect(await view.findByText('두 번째 회원의 분석이에요.')).toBeTruthy();
    await act(async () => resolveOld(jsonResponse(saved())));
    expect(view.queryByText(analysisResponse.overallImpact)).toBeNull();
    expect(fetchMock.mock.calls[0][1]?.signal?.aborted).toBe(true);
  });

  it('비회원 분석 중 로그인하면 늦은 체험 결과를 표시하거나 저장하지 않는다', async () => {
    let resolveGuest!: (response: Response) => void;
    memberFetch((url) =>
      url.endsWith('/api/portfolio/analysis/tasks')
        ? new Promise<Response>((resolve) => {
            resolveGuest = resolve;
          })
        : saved({ analysis: null, analysisId: null, status: 'EMPTY' }),
    );
    const view = render(<PortfolioAnalysis assets={assets} isLoggedIn={false} />);
    fireEvent.click(await view.findByRole('button', { name: '1회 분석 체험하기' }));
    await waitFor(() => expect(resolveGuest).toBeTypeOf('function'));
    view.rerender(<PortfolioAnalysis assets={assets} memberId={1} isLoggedIn />);
    await act(async () => resolveGuest(jsonResponse(analysisResponse)));
    expect(view.queryByText(analysisResponse.overallImpact)).toBeNull();
    expect(sessionStorage.getItem(GUEST_PORTFOLIO_ANALYSIS_STORAGE_KEY)).toBeNull();
  });

  it('자산이 달라지면 과거 분석을 숨기고 현재 자산 분석 API를 호출한다', async () => {
    const fetchMock = memberFetch(() =>
      saved({ matchesCurrentPortfolio: false, status: 'PORTFOLIO_CHANGED' }),
    );
    const view = render(<PortfolioAnalysis assets={[{ assetName: '현대차', weight: 100 }]} />);
    expect(await view.findByText(/자산 구성이 바뀌어 이전 분석을 숨겼어요/)).toBeTruthy();
    expect(view.queryByText('삼성전자')).toBeNull();
    expect(view.getByRole('region', { name: '가상 분석 미리보기' })).toBeTruthy();
    expect(view.queryByText(analysisResponse.overallImpact)).toBeNull();
    const analyzeCurrentAssets = view.getByRole('button', { name: '현재 자산으로 분석하기' });
    expect(analyzeCurrentAssets.classList.contains('is-primary')).toBe(true);
    fireEvent.click(analyzeCurrentAssets);
    await waitFor(() =>
      expect(
        fetchMock.mock.calls.some(
          ([url, init]) => String(url).endsWith('/analysis/tasks') && init?.method === 'POST',
        ),
      ).toBe(true),
    );
    expect(fetchMock.mock.calls.some(([url]) => String(url).endsWith('/refresh'))).toBe(false);
  });

  it('매도 후 새 분석이 실패해도 이전 종목의 분석을 다시 표시하지 않는다', async () => {
    memberFetch((_url, init) =>
      init?.method === 'POST'
        ? jsonResponse({}, 502)
        : saved({ matchesCurrentPortfolio: false, status: 'PORTFOLIO_CHANGED' }),
    );
    const currentAssets = [assets[0], { assetName: '포스코인터내셔널', weight: 40 }];
    const view = render(<PortfolioAnalysis assets={currentAssets} />);
    fireEvent.click(await view.findByRole('button', { name: '현재 자산으로 분석하기' }));
    expect(await view.findByRole('alert')).toBeTruthy();
    expect(view.getByRole('button', { name: '다시 시도' })).toBeTruthy();
    expect(view.queryByText('SK하이닉스')).toBeNull();
    expect(view.queryByText(analysisResponse.overallImpact)).toBeNull();
    expect(view.queryByRole('heading', { name: '전체 포트폴리오' })).toBeNull();
  });

  it('비중 변경 즉시 이전 설명을 숨기고 늦은 조회 결과도 노출하지 않는다', async () => {
    let resolveRead!: (response: Response) => void;
    let reads = 0;
    memberFetch(() =>
      reads++ === 0
        ? saved()
        : new Promise<Response>((resolve) => {
            resolveRead = resolve;
          }),
    );
    const view = render(<PortfolioAnalysis assets={assets} />);
    expect(await view.findByText(analysisResponse.overallImpact)).toBeTruthy();
    view.rerender(
      <PortfolioAnalysis
        assets={[
          { ...assets[0], weight: 70 },
          { ...assets[1], weight: 30 },
        ]}
      />,
    );
    expect(view.queryByText(analysisResponse.overallImpact)).toBeNull();
    await act(async () => resolveRead(jsonResponse(saved())));
    expect(view.queryByText(analysisResponse.overallImpact)).toBeNull();
  });

  it('비회원도 수량이 바뀌면 세션의 이전 분석을 다시 표시하지 않는다', async () => {
    stubTaskFetch(vi.fn(async () => jsonResponse(analysisResponse)));
    const view = render(<PortfolioAnalysis assets={assets} isLoggedIn={false} holdingsKey="1,1" />);
    fireEvent.click(await view.findByRole('button', { name: '1회 분석 체험하기' }));
    expect(await view.findByText(analysisResponse.overallImpact)).toBeTruthy();
    view.rerender(<PortfolioAnalysis assets={assets} isLoggedIn={false} holdingsKey="2,2" />);
    expect(view.queryByText(analysisResponse.overallImpact)).toBeNull();
    expect(view.getByRole('link', { name: '로그인하고 다시 분석하기' })).toBeTruthy();
  });

  it('비중이 같아도 수량 변경 시 이전 분석을 즉시 숨기고 서버 상태를 다시 조회한다', async () => {
    let resolveRead!: (response: Response) => void;
    let reads = 0;
    memberFetch(() =>
      reads++ === 0
        ? saved()
        : new Promise<Response>((resolve) => {
            resolveRead = resolve;
          }),
    );
    const view = render(<PortfolioAnalysis assets={assets} holdingsKey="1,1" />);
    expect(await view.findByText(analysisResponse.overallImpact)).toBeTruthy();
    view.rerender(<PortfolioAnalysis assets={assets} holdingsKey="2,2" />);
    expect(view.queryByText(analysisResponse.overallImpact)).toBeNull();
    await act(async () =>
      resolveRead(
        jsonResponse(saved({ status: 'PORTFOLIO_CHANGED', matchesCurrentPortfolio: false })),
      ),
    );
    expect(view.queryByText(analysisResponse.overallImpact)).toBeNull();
    expect(view.getByRole('button', { name: '현재 자산으로 분석하기' })).toBeTruthy();
  });

  it('연결 실패 뒤 서버에서 완료된 부분 분석도 표시한다', async () => {
    let reads = 0;
    memberFetch((_url, init) => {
      if (init?.method === 'POST') return jsonResponse({}, 504);
      return reads++ === 0
        ? saved({ analysis: null, status: 'EMPTY', analysisId: null })
        : saved({ status: 'RECHECK_REQUIRED' });
    });
    const view = render(<PortfolioAnalysis assets={assets} />);
    fireEvent.click(await view.findByRole('button', { name: 'AI 분석하기' }));
    expect(await view.findByText(analysisResponse.overallImpact)).toBeTruthy();
    expect(view.queryByRole('alert')).toBeNull();
  });

  it.each([false, true])(
    '회원 비동기 작업을 복원하고 핵심 결과와 종합 설명을 차례로 표시한다 (갱신: %s)',
    async (refresh) => {
      let task = refresh
        ? saved()
        : saved({ status: 'EMPTY', analysis: null, analysisId: null, summaryStatus: null });
      const taskPath = `/api/users/me/portfolio/analysis/tasks${refresh ? '/refresh' : ''}`;
      const fetchMock = memberFetch((url, init) => {
        if (init?.method === 'POST') {
          expect(url.endsWith(taskPath)).toBe(true);
          task = { ...task, status: 'PROCESSING' };
          return jsonResponse(task, 202);
        }
        expect(url.endsWith('/api/users/me/portfolio/analysis')).toBe(true);
        return task;
      });
      const first = render(<PortfolioAnalysis assets={assets} />);
      fireEvent.click(
        await first.findByRole('button', { name: refresh ? '새 소식 확인' : 'AI 분석하기' }),
      );
      await first.findByRole('button', { name: '진행 상태 확인' });
      expect(first.queryByText(analysisResponse.overallImpact)).toBeNull();
      expect(first.queryByRole('heading', { name: '자산별 영향' })).toBeNull();
      first.unmount();

      const second = render(<PortfolioAnalysis assets={assets} />);
      fireEvent.click(await second.findByRole('button', { name: '진행 상태 확인' }));
      expect(second.queryByText(analysisResponse.overallImpact)).toBeNull();
      await waitFor(() =>
        expect(
          second.getByRole('button', { name: '진행 상태 확인' }).hasAttribute('disabled'),
        ).toBe(false),
      );
      task = saved({
        status: 'PROCESSING',
        analysisId: 2,
        analysis: { ...analysisResponse, overallImpact: '' },
        summaryStatus: 'PENDING',
      });
      await second.findByText(
        '점수와 종목별 근거가 준비됐어요. 종합 설명을 작성하고 있어요.',
        {},
        { timeout: 4500 },
      );
      expect(second.getByRole('heading', { name: '전체 포트폴리오' })).toBeTruthy();
      expect(second.queryByText(/분석에 약 30초 정도 걸려요/)).toBeNull();
      fireEvent.click(second.getByText('삼성전자').closest('button')!);
      expect(second.getByText(analysisResponse.impacts[0].expectedReaction)).toBeTruthy();
      fireEvent.click(second.getByRole('button', { name: /^전체 포트폴리오 분석 보기$/ }));
      task = saved({ analysisId: 2, summaryStatus: 'READY' });
      await second.findByText(analysisResponse.overallImpact, {}, { timeout: 4500 });
      expect(fetchMock.mock.calls.filter(([, init]) => init?.method === 'POST')).toHaveLength(1);
      expect(second.queryByText(/종합 설명을 작성하고 있어요/)).toBeNull();
    },
    12000,
  );

  it('회원 종합 설명만 실패하면 핵심 분석 결과를 유지한다', async () => {
    const fetchMock = memberFetch(() =>
      saved({ summaryStatus: 'FAILED', analysis: { ...analysisResponse, overallImpact: '' } }),
    );
    const view = render(<PortfolioAnalysis assets={assets} />);
    await view.findByText('종합 설명을 불러오지 못했어요. 점수와 종목별 근거는 확인할 수 있어요.');
    expect(view.getByRole('heading', { name: '전체 포트폴리오' })).toBeTruthy();
    expect(fetchMock.mock.calls.every(([, init]) => !init?.method)).toBe(true);
  });

  it('요청 시간 초과 후 처리 중 상태를 조회하고 완료 결과를 자동으로 표시한다', async () => {
    let reads = 0;
    const fetchMock = memberFetch((_url, init) => {
      if (init?.method === 'POST') return jsonResponse({}, 504);
      reads++;
      if (reads === 1) return saved({ analysis: null, status: 'EMPTY', analysisId: null });
      if (reads === 2) return saved({ analysis: null, status: 'PROCESSING', analysisId: null });
      return saved();
    });
    const view = render(<PortfolioAnalysis assets={assets} />);
    fireEvent.click(await view.findByRole('button', { name: 'AI 분석하기' }));
    const processingMessage = await view.findByText(
      '분석에 약 30초 정도 걸려요. 다른 화면을 둘러보거나 새로고침해도 분석은 계속돼요.',
    );
    const processingStatus = processingMessage.closest('[role="status"]');
    expect(processingStatus?.querySelector('.portfolio-analysis-spinner')).toBeTruthy();
    expect(view.queryByRole('alert')).toBeNull();
    expect(
      await view.findByText(analysisResponse.overallImpact, {}, { timeout: 4500 }),
    ).toBeTruthy();
    expect(view.queryByText(processingMessage.textContent!)).toBeNull();
    expect(
      fetchMock.mock.calls.filter(
        ([url, init]) => String(url).endsWith('/analysis/tasks') && init?.method === 'POST',
      ),
    ).toHaveLength(1);
    view.unmount();
    const calls = fetchMock.mock.calls.length;
    await new Promise((resolve) => setTimeout(resolve, 50));
    expect(fetchMock).toHaveBeenCalledTimes(calls);
  });

  it('다른 탭에서 처리 중이면 진행 상태만 조회하고 새 분석 요청은 보내지 않는다', async () => {
    const fetchMock = memberFetch(() => saved({ status: 'PROCESSING' }));
    const view = render(<PortfolioAnalysis assets={assets} />);
    fireEvent.click(await view.findByRole('button', { name: '진행 상태 확인' }));
    await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(2));
    expect(fetchMock.mock.calls.every(([, init]) => !init?.method)).toBe(true);
  });

  it('갱신 중 계정이 바뀌면 표시 중인 결과와 늦은 갱신 응답을 모두 제거한다', async () => {
    let resolveRefresh!: (response: Response) => void;
    let reads = 0;
    memberFetch((url) => {
      if (url.endsWith('/refresh'))
        return new Promise<Response>((resolve) => {
          resolveRefresh = resolve;
        });
      return reads++ === 0 ? saved() : saved({ analysis: null, status: 'EMPTY', analysisId: null });
    });
    const view = render(<PortfolioAnalysis memberId={1} assets={assets} />);
    fireEvent.click(await view.findByRole('button', { name: '새 소식 확인' }));
    await waitFor(() => expect(resolveRefresh).toBeDefined());
    view.rerender(<PortfolioAnalysis memberId={2} assets={assets} />);
    expect(view.queryByText(analysisResponse.overallImpact)).toBeNull();
    await act(async () => resolveRefresh(jsonResponse(saved())));
    expect(view.queryByText(analysisResponse.overallImpact)).toBeNull();
  });

  it('동시 요청 충돌은 서버 상태를 조회해 처리 중으로 안내한다', async () => {
    let reads = 0;
    const fetchMock = memberFetch((url) =>
      url.endsWith('/refresh')
        ? jsonResponse({ code: 'PORTFOLIO_ANALYSIS_IN_PROGRESS' }, 409)
        : saved({ status: reads++ === 0 ? 'READY' : 'PROCESSING' }),
    );
    const view = render(<PortfolioAnalysis assets={assets} />);
    fireEvent.click(await view.findByRole('button', { name: '새 소식 확인' }));
    expect(await view.findByRole('button', { name: '진행 상태 확인' })).toBeTruthy();
    expect(view.queryByText(analysisResponse.overallImpact)).toBeNull();
    expect(fetchMock.mock.calls.filter(([url]) => String(url).endsWith('/refresh'))).toHaveLength(
      1,
    );
  });

  it('일부 근거 부족은 중립으로 바꾸지 않고 다른 종목 결과를 유지한다', async () => {
    memberFetch(() =>
      saved({
        analysis: {
          ...analysisResponse,
          overallDirection: null,
          overallScore: null,
          analyzedAssetCount: 1,
          coverage: 'PARTIAL',
          analyzedWeight: 60,
          impacts: [
            analysisResponse.impacts[0],
            {
              ...analysisResponse.impacts[1],
              direction: null,
              impactLevel: null,
              sources: [],
              expectedReaction: '',
              outlook: '',
            },
          ],
        },
      }),
    );
    const view = render(<PortfolioAnalysis assets={assets} />);
    expect(await view.findByRole('heading', { name: '전체 포트폴리오' })).toBeTruthy();
    fireEvent.click(view.getByText('SK하이닉스').closest('button')!);
    expect(view.getByText('판단할 근거가 충분하지 않아요.')).toBeTruthy();
    fireEvent.click(view.getByText('삼성전자').closest('button')!);
    expect(view.getByText(analysisResponse.impacts[0].expectedReaction)).toBeTruthy();
  });
});

function saved(overrides: Record<string, unknown> = {}) {
  return {
    status: 'READY',
    lastCheckOutcome: 'UPDATED',
    analysisId: 1,
    matchesCurrentPortfolio: true,
    lastSucceededAt: '2026-10-05T08:00:00Z',
    lastFailedAt: null,
    failureCode: null,
    changeReason: null,
    analysis: analysisResponse,
    ...overrides,
  };
}

const emptyTask = {
  taskId: null,
  status: 'EMPTY',
  analysis: null,
  summaryStatus: null,
  failureCode: null,
  retryAllowed: false,
};
function stubTaskFetch(respond: (...args: any[]) => unknown) {
  vi.stubGlobal(
    'fetch',
    vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      if (String(input).endsWith('/api/portfolio/analysis/tasks') && init?.method !== 'POST')
        return jsonResponse(emptyTask);
      return respond(input, init);
    }),
  );
}

function memberFetch(respond: (url: string, init?: RequestInit) => unknown) {
  const mock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const url = String(input);
    if (url.endsWith('/api/portfolio/analysis/tasks') && init?.method !== 'POST')
      return jsonResponse(emptyTask);
    if (url.endsWith('/api/auth/csrf'))
      return jsonResponse({ headerName: 'X-CSRF-TOKEN', token: 'test' });
    const response = await respond(url, init);
    return response instanceof Response ? response : jsonResponse(response);
  });
  vi.stubGlobal('fetch', mock);
  return mock;
}

describe('포트폴리오 분석 이벤트', () => {
  it.each([
    ['READY', 'READY'],
    ['PROCESSING', 'PROCESSING'],
    ['RECHECK_REQUIRED', 'RECHECK_REQUIRED'],
    ['FAILED', 'ERROR'],
  ])('504 이후 서버 상태 %s를 복구하면 결과를 %s로 한 번 기록한다', async (status, expected) => {
    let reads = 0;
    memberFetch((_url, init) => {
      if (init?.method === 'POST') return jsonResponse({}, 504);
      return reads++ === 0
        ? saved({ analysis: null, status: 'EMPTY', analysisId: null })
        : saved({ status });
    });
    const view = render(<PortfolioAnalysis assets={assets} />);
    fireEvent.click(await view.findByRole('button', { name: 'AI 분석하기' }));
    await waitFor(() =>
      expect(trackEvent).toHaveBeenCalledWith(
        'portfolio_analysis_result',
        expect.objectContaining({ status: expected }),
      ),
    );
    expect(
      vi.mocked(trackEvent).mock.calls.filter(([name]) => name === 'portfolio_analysis_result'),
    ).toHaveLength(1);
    if (expected !== 'ERROR') expect(view.queryByRole('alert')).toBeNull();
  });

  it.each(['READY', 'PROCESSING', 'RECHECK_REQUIRED', 'FAILED'])(
    '복원은 시작으로 세지 않고 갱신 결과 %s를 그대로 기록한다',
    async (status) => {
      memberFetch((url) =>
        saved(url.endsWith('/refresh') ? { status, lastCheckOutcome: 'UNCHANGED' } : {}),
      );
      const view = render(<PortfolioAnalysis assets={assets} />);
      const button = await view.findByRole('button', { name: '새 소식 확인' });
      expect(trackEvent).not.toHaveBeenCalled();
      fireEvent.click(button);
      await waitFor(() =>
        expect(trackEvent).toHaveBeenCalledWith(
          'portfolio_analysis_result',
          expect.objectContaining({
            status,
            last_check_outcome: 'UNCHANGED',
            action: 'refresh',
            duration_ms: expect.any(Number),
          }),
        ),
      );
      expect(
        vi.mocked(trackEvent).mock.calls.filter(([name]) => name === 'start_portfolio_analysis'),
      ).toHaveLength(1);
      expect(JSON.stringify(vi.mocked(trackEvent).mock.calls)).not.toContain('삼성전자');
    },
  );

  it('409 이후 상태 복원은 하나의 CONFLICT 결과로 집계한다', async () => {
    memberFetch((url) => (url.endsWith('/refresh') ? jsonResponse({}, 409) : saved()));
    const view = render(<PortfolioAnalysis assets={assets} />);
    fireEvent.click(await view.findByRole('button', { name: '새 소식 확인' }));
    await waitFor(() =>
      expect(trackEvent).toHaveBeenCalledWith(
        'portfolio_analysis_result',
        expect.objectContaining({ status: 'CONFLICT' }),
      ),
    );
    await view.findByRole('button', { name: '새 소식 확인' });
    expect(
      vi.mocked(trackEvent).mock.calls.filter(([name]) => name === 'portfolio_analysis_result'),
    ).toHaveLength(1);
  });

  it('화면 이탈로 취소된 분석은 결과로 집계하지 않는다', async () => {
    let resolve!: (response: Response) => void;
    stubTaskFetch(
      vi.fn(
        () =>
          new Promise<Response>((done) => {
            resolve = done;
          }),
      ),
    );
    const view = render(<PortfolioAnalysis assets={assets} isLoggedIn={false} />);
    fireEvent.click(await view.findByRole('button', { name: '1회 분석 체험하기' }));
    await waitFor(() => expect(resolve).toBeTypeOf('function'));
    view.unmount();
    await act(async () => resolve(jsonResponse(analysisResponse)));
    expect(vi.mocked(trackEvent).mock.calls.map(([name]) => name)).toEqual([
      'start_portfolio_analysis',
    ]);
  });

  it('결과의 실제 노출과 자산 선택·출처 열기를 집계한다', async () => {
    let intersect!: IntersectionObserverCallback;
    const observe = vi.fn();
    vi.stubGlobal(
      'IntersectionObserver',
      class {
        constructor(callback: IntersectionObserverCallback) {
          intersect = callback;
        }
        observe = observe;
        disconnect() {}
      },
    );
    memberFetch(() => saved());
    const view = render(<PortfolioAnalysis assets={assets} />);
    await view.findByText(analysisResponse.overallImpact);
    // 결과 렌더링 이후 실제 관찰 대상 등록까지 기다린다.
    await waitFor(() =>
      expect(observe).toHaveBeenCalledWith(
        view.container.querySelector('.portfolio-analysis-layout'),
      ),
    );
    expect(trackEvent).not.toHaveBeenCalled();
    act(() =>
      intersect(
        [{ isIntersecting: true, intersectionRatio: 1 } as IntersectionObserverEntry],
        {} as IntersectionObserver,
      ),
    );
    act(() =>
      intersect(
        [{ isIntersecting: true, intersectionRatio: 1 } as IntersectionObserverEntry],
        {} as IntersectionObserver,
      ),
    );
    expect(
      vi.mocked(trackEvent).mock.calls.filter(([name]) => name === 'view_portfolio_analysis'),
    ).toHaveLength(1);
    const assetButton = view.getByText('삼성전자').closest('button')!;
    fireEvent.click(assetButton);
    expect(trackEvent).toHaveBeenCalledWith(
      'select_portfolio_analysis_asset',
      expect.objectContaining({ scope: 'asset', position: 1 }),
    );
    const details = view.container.querySelector<HTMLDetailsElement>('.portfolio-evidence')!;
    details.open = true;
    fireEvent(details, new Event('toggle'));
    expect(trackEvent).toHaveBeenCalledWith(
      'expand_portfolio_analysis_sources',
      expect.objectContaining({ scope: 'asset', source_count: 1 }),
    );
  });
});
