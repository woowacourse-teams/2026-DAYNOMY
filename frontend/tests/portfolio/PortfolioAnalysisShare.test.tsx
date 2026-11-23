/** @vitest-environment jsdom */

import { cleanup, fireEvent, render, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import { AuthContext } from '../../src/auth/AuthContext';
import { PortfolioAnalysisSharePanel } from '../../src/features/portfolio-review/PortfolioAnalysisSharePanel';
import PortfolioAnalysisSharePage from '../../src/features/portfolio-review/PortfolioAnalysisSharePage';
import { getPublicPortfolioAnalysisShare } from '../../src/features/portfolio-review/api';

vi.mock('../../src/analytics', () => ({ trackEvent: vi.fn() }));

const originalShowModal = HTMLDialogElement.prototype.showModal;
const originalClose = HTMLDialogElement.prototype.close;

function response(body: unknown, status = 200) {
  return new Response(status === 204 ? null : JSON.stringify(body), {
    status,
    headers: { 'content-type': 'application/json' },
  });
}

const comment = {
  id: 9,
  authorNickname: '리뷰어',
  content: '반도체 비중을 조금 줄이는 방향도 검토해 보세요.',
  createdAt: '2026-11-12T01:00:00Z',
  updatedAt: '2026-11-12T01:00:00Z',
  replies: [],
};

const analysisAssets = [
  { assetName: '삼성전자', weight: 60 },
  { assetName: 'SK하이닉스', weight: 40 },
];

const ownerShare = {
  id: 3,
  analysisId: 7,
  question: '반도체 비중이 너무 높은지 의견을 듣고 싶어요.',
  additionalExplanation: null,
  additionalExplanationUpdatedAt: null,
  status: 'ACTIVE',
  expiresAt: '2026-11-19T00:00:00Z',
  createdAt: '2026-11-12T00:00:00Z',
  allAssetsShared: true,
  sharedAssets: analysisAssets,
  comments: [],
};

const publicShare = {
  ownerNickname: '작성자',
  question: ownerShare.question,
  additionalExplanation: null,
  additionalExplanationUpdatedAt: null,
  expiresAt: ownerShare.expiresAt,
  canComment: false,
  myComments: [],
  ownerShare: null,
  analysis: {
    totalAssetCount: 1,
    analyzedAssetCount: 1,
    allAssetsShared: false,
    hasAnalysis: true,
    overallDirection: null,
    overallScore: null,
    positiveImpactScore: 0,
    negativeImpactScore: 0,
    analyzedAt: '2026-11-12T00:00:00Z',
    overallImpact: '',
    impacts: [
      {
        assetName: '삼성전자',
        weight: 60,
        direction: 'POSITIVE',
        impactLevel: 'MEDIUM',
        issueSummary: '반도체 수요가 회복되고 있어요.',
        expectedReaction: '실적 기대가 높아질 수 있어요.',
        outlook: '수요 추이를 확인해야 해요.',
        reason: '반도체 업황의 영향을 크게 받아요.',
        evidenceSentence: '반도체 수요가 증가했어요.',
        sources: [
          {
            title: '반도체 산업 동향',
            url: 'https://example.com/source',
            excerpt: '수요가 증가했습니다.',
          },
        ],
        holding: {
          assetCode: '005930',
          market: 'KOSPI',
          firstPurchasedOn: '2026-01-10',
          returnRate: 14.29,
        },
        rank: 1,
      },
    ],
    sources: [],
    excludedAssets: [],
  },
};

beforeEach(() => {
  HTMLDialogElement.prototype.showModal = function () {
    this.setAttribute('open', '');
  };
  HTMLDialogElement.prototype.close = function () {
    this.removeAttribute('open');
  };
});

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
  HTMLDialogElement.prototype.showModal = originalShowModal;
  HTMLDialogElement.prototype.close = originalClose;
});

describe('포트폴리오 분석 의견 요청', () => {
  it.each([
    { additionalExplanation: undefined, additionalExplanationUpdatedAt: undefined },
    { additionalExplanation: 123, additionalExplanationUpdatedAt: comment.updatedAt },
    { additionalExplanation: '설명', additionalExplanationUpdatedAt: null },
    { additionalExplanation: null, additionalExplanationUpdatedAt: comment.updatedAt },
    { additionalExplanation: '설명', additionalExplanationUpdatedAt: 'invalid-date' },
    { additionalExplanation: '', additionalExplanationUpdatedAt: comment.updatedAt },
    { additionalExplanation: 'a'.repeat(2001), additionalExplanationUpdatedAt: comment.updatedAt },
  ])('잘못된 공개 설명 응답을 거부한다', async (explanation) => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(response({ ...publicShare, ...explanation }))
      .mockResolvedValueOnce(
        response({ ...publicShare, ownerShare: { ...ownerShare, ...explanation } }),
      );
    vi.stubGlobal('fetch', fetchMock);
    await expect(getPublicPortfolioAnalysisShare('test-token')).rejects.toThrow(
      '공유된 분석 응답 형식이 올바르지 않습니다.',
    );
    await expect(getPublicPortfolioAnalysisShare('test-token')).rejects.toThrow(
      '공유된 분석 응답 형식이 올바르지 않습니다.',
    );
  });

  it('비회원에게 공개 설명을 질문 아래 표시하고 댓글과 분리한다', async () => {
    const explanation = '3년 이상 투자할 계획입니다.\n분산도 함께 고민하고 있어요.';
    vi.stubGlobal(
      'fetch',
      vi.fn(() =>
        Promise.resolve(
          response({
            ...publicShare,
            additionalExplanation: explanation,
            additionalExplanationUpdatedAt: comment.updatedAt,
          }),
        ),
      ),
    );
    const view = render(
      <MemoryRouter initialEntries={['/portfolio/analysis/share#test-token']}>
        <AuthContext.Provider
          value={{ isLoggedIn: false, memberId: null, loading: false, role: null }}
        >
          <PortfolioAnalysisSharePage />
        </AuthContext.Provider>
      </MemoryRouter>,
    );
    const section = await view.findByRole('region', { name: '작성자 추가 설명' });
    expect(section.textContent).toContain(explanation);
    expect(section.closest('.portfolio-public-share-hero')).not.toBeNull();
    expect(view.queryByRole('button', { name: '설명 수정' })).toBeNull();
    expect(view.queryByText(comment.content)).toBeNull();
  });

  it('관리 창에서 공개 설명을 작성하고 실패 시 유지하며 수정하거나 내릴 수 있다', async () => {
    let attempts = 0;
    const fetchMock = vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input);
      if (url.endsWith('/api/auth/csrf'))
        return Promise.resolve(response({ headerName: 'X-XSRF-TOKEN', token: 'csrf' }));
      if (url.endsWith('/api/users/me/portfolio/analysis/shares') && !init?.method)
        return Promise.resolve(response([ownerShare]));
      if (
        url.endsWith('/api/users/me/portfolio/analysis/shares/3/explanation') &&
        init?.method === 'PUT'
      ) {
        attempts += 1;
        if (attempts === 1)
          return Promise.resolve(
            response({ code: 'INTERNAL_SERVER_ERROR', message: '저장 실패' }, 500),
          );
        const content = JSON.parse(String(init.body)).content;
        return Promise.resolve(
          response({
            ...ownerShare,
            additionalExplanation: content || null,
            additionalExplanationUpdatedAt: content ? comment.updatedAt : null,
          }),
        );
      }
      throw new Error(`unexpected request: ${url}`);
    });
    vi.stubGlobal('fetch', fetchMock);
    const view = render(<PortfolioAnalysisSharePanel analysisId={7} assets={analysisAssets} />);
    fireEvent.click(view.getByRole('button', { name: '의견 받기' }));
    fireEvent.click(await view.findByRole('button', { name: '설명 추가' }));
    expect(view.getByText('링크를 가진 모든 사람에게 공개돼요.')).toBeTruthy();
    const input = view.getByRole('textbox', { name: '추가 설명 내용' });
    expect(view.getByRole('button', { name: '추가 설명 공개' }).hasAttribute('disabled')).toBe(
      true,
    );
    fireEvent.change(input, { target: { value: '<잘못된 설명>' } });
    expect(view.getByRole('button', { name: '추가 설명 공개' }).hasAttribute('disabled')).toBe(
      true,
    );
    fireEvent.change(input, { target: { value: '장기 투자 목적입니다.' } });
    fireEvent.click(view.getByRole('button', { name: '추가 설명 공개' }));
    await view.findByRole('alert');
    expect((input as HTMLTextAreaElement).value).toBe('장기 투자 목적입니다.');
    fireEvent.click(view.getByRole('button', { name: '추가 설명 공개' }));
    expect(await view.findByText('공유 페이지에 추가 설명을 공개했어요.')).toBeTruthy();
    expect(view.getByText('장기 투자 목적입니다.')).toBeTruthy();
    expect(view.queryByRole('textbox', { name: '추가 설명 내용' })).toBeNull();
    fireEvent.click(view.getByRole('button', { name: '설명 수정' }));
    fireEvent.change(view.getByRole('textbox', { name: '추가 설명 내용' }), {
      target: { value: '' },
    });
    fireEvent.click(view.getByRole('button', { name: '설명 내리기' }));
    expect(await view.findByText('추가 설명을 내렸어요.')).toBeTruthy();
    expect(view.getByRole('button', { name: '설명 추가' })).toBeTruthy();
    expect(attempts).toBe(3);
  });

  it.each([
    undefined,
    'invalid',
    {},
    { ...ownerShare, status: 'UNKNOWN' },
    { ...ownerShare, comments: null },
    { ...ownerShare, comments: [{ ...comment, replies: [{}] }] },
  ])('잘못된 작성자 전용 응답을 거부한다: %j', async (ownerData) => {
    vi.stubGlobal(
      'fetch',
      vi.fn(() => Promise.resolve(response({ ...publicShare, ownerShare: ownerData }))),
    );
    await expect(getPublicPortfolioAnalysisShare('test-token')).rejects.toThrow(
      '공유된 분석 응답 형식이 올바르지 않습니다.',
    );
  });
  it('작성자는 링크에서 받은 의견을 읽고 답글을 작성한다', async () => {
    const reply = {
      id: 31,
      authorNickname: '작성자',
      content: '의견 감사합니다.',
      fromOwner: true,
      editable: true,
      createdAt: comment.createdAt,
      updatedAt: comment.updatedAt,
    };
    const fetchMock = vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input);
      if (url.endsWith('/api/portfolio/analysis/shares'))
        return Promise.resolve(
          response({ ...publicShare, ownerShare: { ...ownerShare, comments: [comment] } }),
        );
      if (url.endsWith('/api/auth/csrf'))
        return Promise.resolve(response({ headerName: 'X-XSRF-TOKEN', token: 'csrf' }));
      if (
        url.endsWith('/api/users/me/portfolio/analysis/shares/3/comments/9/replies') &&
        init?.method === 'POST'
      )
        return Promise.resolve(response({ ...comment, replies: [reply] }));
      if (
        url.endsWith('/api/users/me/portfolio/analysis/comment-replies/31') &&
        init?.method === 'PUT'
      )
        return Promise.resolve(
          response({ ...comment, replies: [{ ...reply, content: '답글을 수정했어요.' }] }),
        );
      if (
        url.endsWith('/api/users/me/portfolio/analysis/comment-replies/31') &&
        init?.method === 'DELETE'
      )
        return Promise.resolve(response(comment));
      throw new Error(`unexpected request: ${url}`);
    });
    vi.stubGlobal('fetch', fetchMock);
    vi.spyOn(window, 'confirm').mockReturnValue(true);
    const view = render(
      <MemoryRouter initialEntries={['/portfolio/analysis/share#owner-token']}>
        <AuthContext.Provider value={{ isLoggedIn: true, memberId: 1, loading: false, role: null }}>
          <PortfolioAnalysisSharePage />
        </AuthContext.Provider>
      </MemoryRouter>,
    );
    expect(await view.findByRole('heading', { name: '받은 의견 1개' })).toBeTruthy();
    expect(view.getByText(comment.content)).toBeTruthy();
    expect(view.queryByRole('textbox', { name: '새 댓글' })).toBeNull();
    expect(view.queryByRole('textbox', { name: '리뷰어님 댓글에 답글' })).toBeNull();
    expect(view.getByRole('button', { name: '답글 달기' }).getAttribute('aria-expanded')).toBe(
      'false',
    );
    fireEvent.click(view.getByRole('button', { name: '답글 달기' }));
    expect(document.activeElement).toBe(
      view.getByRole('textbox', { name: '리뷰어님 댓글에 답글' }),
    );
    expect(view.getByRole('button', { name: '답글 등록' }).hasAttribute('disabled')).toBe(true);
    fireEvent.change(view.getByRole('textbox', { name: '리뷰어님 댓글에 답글' }), {
      target: { value: reply.content },
    });
    fireEvent.click(view.getByRole('button', { name: '답글 접기' }));
    expect(view.queryByRole('textbox', { name: '리뷰어님 댓글에 답글' })).toBeNull();
    fireEvent.click(view.getByRole('button', { name: '답글 달기' }));
    expect(
      (view.getByRole('textbox', { name: '리뷰어님 댓글에 답글' }) as HTMLTextAreaElement).value,
    ).toBe(reply.content);
    fireEvent.click(view.getByRole('button', { name: '답글 등록' }));
    expect(await view.findByText(reply.content)).toBeTruthy();
    expect(view.queryByRole('textbox', { name: '리뷰어님 댓글에 답글' })).toBeNull();
    expect(document.activeElement).toBe(view.getByRole('button', { name: '답글 달기' }));
    fireEvent.click(view.getByRole('button', { name: '답글 달기' }));
    expect(
      (view.getByRole('textbox', { name: '리뷰어님 댓글에 답글' }) as HTMLTextAreaElement).value,
    ).toBe('');
    expect(view.getByRole('button', { name: '답글 등록' }).hasAttribute('disabled')).toBe(true);
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/users/me/portfolio/analysis/shares/3/comments/9/replies',
      expect.objectContaining({ method: 'POST', body: JSON.stringify({ content: reply.content }) }),
    );
    fireEvent.click(view.getByRole('button', { name: /^수정$/ }));
    fireEvent.change(view.getByRole('textbox', { name: '작성자 답글 수정' }), {
      target: { value: '답글을 수정했어요.' },
    });
    fireEvent.click(view.getByRole('button', { name: '수정 저장' }));
    expect(await view.findByText('답글을 수정했어요.')).toBeTruthy();
    fireEvent.click(view.getByRole('button', { name: /^삭제$/ }));
    await waitFor(() => expect(view.queryByText('답글을 수정했어요.')).toBeNull());
  });

  it('받은 의견이 없는 작성자에게 빈 상태를 표시한다', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(() => Promise.resolve(response({ ...publicShare, ownerShare }))),
    );
    const view = render(
      <MemoryRouter initialEntries={['/portfolio/analysis/share#owner-token']}>
        <AuthContext.Provider value={{ isLoggedIn: true, memberId: 1, loading: false, role: null }}>
          <PortfolioAnalysisSharePage />
        </AuthContext.Provider>
      </MemoryRouter>,
    );
    expect(await view.findByRole('heading', { name: '받은 의견 0개' })).toBeTruthy();
    expect(view.getByText(/아직 받은 의견이 없어요/)).toBeTruthy();
  });

  it('질문으로 7일 공유 링크를 발급한다', async () => {
    const fetchMock = vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input);
      if (url.endsWith('/api/users/me/portfolio/analysis/shares') && !init?.method)
        return Promise.resolve(response([]));
      if (url.endsWith('/api/auth/csrf'))
        return Promise.resolve(response({ headerName: 'X-XSRF-TOKEN', token: 'csrf' }));
      if (url.endsWith('/api/users/me/portfolio/analysis/shares') && init?.method === 'POST')
        return Promise.resolve(response({ share: ownerShare, token: 'raw-share-token' }));
      throw new Error(`unexpected request: ${url}`);
    });
    vi.stubGlobal('fetch', fetchMock);

    const view = render(<PortfolioAnalysisSharePanel analysisId={7} assets={analysisAssets} />);
    fireEvent.click(view.getByRole('button', { name: '의견 받기' }));
    const textarea = await view.findByRole('textbox', { name: '어떤 점이 궁금한가요?' });
    fireEvent.change(textarea, { target: { value: '어때?' } });
    fireEvent.click(view.getByRole('checkbox', { name: /SK하이닉스/ }));
    fireEvent.click(view.getByRole('button', { name: '7일 링크 만들기' }));

    const link = await view.findByLabelText('공유 링크');
    expect((link as HTMLInputElement).value).toContain('/portfolio/analysis/share#raw-share-token');
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/users/me/portfolio/analysis/shares',
      expect.objectContaining({
        method: 'POST',
        body: JSON.stringify({
          analysisId: 7,
          question: '어때?',
          assetNames: ['삼성전자'],
        }),
      }),
    );
  });

  it('기존 링크를 유지한 채 새 링크를 추가 발급한다', async () => {
    const nextShare = {
      ...ownerShare,
      id: 4,
      createdAt: '2026-11-13T00:00:00Z',
      comments: [],
    };
    const fetchMock = vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input);
      if (url.endsWith('/api/users/me/portfolio/analysis/shares') && !init?.method)
        return Promise.resolve(response([ownerShare]));
      if (url.endsWith('/api/auth/csrf'))
        return Promise.resolve(response({ headerName: 'X-XSRF-TOKEN', token: 'csrf' }));
      if (url.endsWith('/api/users/me/portfolio/analysis/shares') && init?.method === 'POST')
        return Promise.resolve(response({ share: nextShare, token: 'next-share-token' }));
      throw new Error(`unexpected request: ${url}`);
    });
    vi.stubGlobal('fetch', fetchMock);

    const view = render(<PortfolioAnalysisSharePanel analysisId={7} assets={analysisAssets} />);
    fireEvent.click(view.getByRole('button', { name: '의견 받기' }));

    const issueButton = await view.findByRole('button', { name: '새 링크 만들기' });
    expect(view.queryByText('새 링크를 발급하면 이전 링크는 바로 사용할 수 없어요.')).toBeNull();
    fireEvent.click(issueButton);

    const link = await view.findByLabelText('공유 링크');
    expect((link as HTMLInputElement).value).toContain(
      '/portfolio/analysis/share#next-share-token',
    );
    expect(view.getAllByText('공유 중')).toHaveLength(2);
  });

  it('분석 전에는 포트폴리오 구성만 공유한다', async () => {
    const compositionShare = { ...ownerShare, analysisId: null };
    const fetchMock = vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input);
      if (url.endsWith('/api/users/me/portfolio/analysis/shares') && !init?.method)
        return Promise.resolve(response([]));
      if (url.endsWith('/api/auth/csrf'))
        return Promise.resolve(response({ headerName: 'X-XSRF-TOKEN', token: 'csrf' }));
      if (url.endsWith('/api/users/me/portfolio/analysis/shares') && init?.method === 'POST')
        return Promise.resolve(response({ share: compositionShare, token: 'composition-token' }));
      throw new Error(`unexpected request: ${url}`);
    });
    vi.stubGlobal('fetch', fetchMock);

    const view = render(<PortfolioAnalysisSharePanel analysisId={null} assets={analysisAssets} />);
    fireEvent.click(view.getByRole('button', { name: '의견 받기' }));
    fireEvent.change(await view.findByRole('textbox', { name: '어떤 점이 궁금한가요?' }), {
      target: { value: ownerShare.question },
    });
    fireEvent.click(view.getByRole('button', { name: '7일 링크 만들기' }));

    await view.findByLabelText('공유 링크');
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/users/me/portfolio/analysis/shares',
      expect.objectContaining({
        method: 'POST',
        body: JSON.stringify({
          analysisId: null,
          question: ownerShare.question,
          assetNames: ['삼성전자', 'SK하이닉스'],
        }),
      }),
    );
  });

  it('분석 없는 공유 페이지도 비중과 보유 맥락을 표시한다', async () => {
    const fetchMock = vi.fn(() =>
      Promise.resolve(
        response({
          ...publicShare,
          ownerNickname: '회원_fa09e0814ba642d7',
          analysis: {
            ...publicShare.analysis,
            analyzedAssetCount: 0,
            hasAnalysis: false,
            analyzedAt: null,
            impacts: publicShare.analysis.impacts.map((impact) => ({
              ...impact,
              direction: null,
              impactLevel: null,
              issueSummary: '',
              reason: '',
              sources: [],
            })),
          },
        }),
      ),
    );
    vi.stubGlobal('fetch', fetchMock);

    const view = render(
      <MemoryRouter initialEntries={['/portfolio/analysis/share#composition-token']}>
        <AuthContext.Provider
          value={{ isLoggedIn: false, memberId: null, loading: false, role: null }}
        >
          <PortfolioAnalysisSharePage />
        </AuthContext.Provider>
      </MemoryRouter>,
    );

    const privacyNote = await view.findByText(/공유 시점의 종목과 비중이에요/);
    expect(privacyNote.closest('details')).toBeNull();
    expect(privacyNote.textContent).toContain(
      '매수가·공유 당시 가격·수량·평가금액·손익 금액은 비공개입니다.',
    );
    expect(view.getByRole('heading', { name: '공개한 종목 1개' })).toBeTruthy();
    const allocation = view.getByRole('img', {
      name: '전체 포트폴리오 기준 공개 종목 비중: 삼성전자 60%',
    });
    expect(allocation.querySelector('span')?.style.flexBasis).toBe('60%');
    expect(view.getByText('전체 포트폴리오 기준 비중이며, 선택한 종목만 표시해요.')).toBeTruthy();
    expect(view.queryByRole('heading', { name: '포트폴리오 구성' })).toBeNull();
    expect(view.queryByText('70,000원')).toBeNull();
    expect(view.queryByText('80,000원')).toBeNull();
    expect(view.getByText('+14.29%')).toBeTruthy();
    expect(view.getByText('2026.01.10')).toBeTruthy();
    expect(view.getByText('익명 투자자')).toBeTruthy();
    expect(view.queryByText('포트폴리오 공유')).toBeNull();
    expect(view.queryByText(/회원_fa09/)).toBeNull();
    expect(view.getByText('링크 공개 기한 · 11월 19일까지')).toBeTruthy();
    expect(view.queryByText('포트폴리오 구성과 보유 현황을 공유했어요.')).toBeNull();
    expect(view.queryByText('공유 당시 가격')).toBeNull();
    expect(view.queryByText('반도체 수요가 회복되고 있어요.')).toBeNull();
  });

  it('과거 분석의 의견 요청과 받은 의견도 조회하고 공유를 중단한다', async () => {
    const pastShare = { ...ownerShare, analysisId: 6, comments: [comment] };
    const fetchMock = vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input);
      if (url.endsWith('/api/users/me/portfolio/analysis/shares') && !init?.method)
        return Promise.resolve(response([pastShare]));
      if (url.endsWith('/api/auth/csrf'))
        return Promise.resolve(response({ headerName: 'X-XSRF-TOKEN', token: 'csrf' }));
      if (url.endsWith(`/api/users/me/portfolio/analysis/shares/${pastShare.id}/revoke`))
        return Promise.resolve(response({ ...pastShare, status: 'REVOKED' }));
      throw new Error(`unexpected request: ${url}`);
    });
    vi.stubGlobal('fetch', fetchMock);

    const view = render(<PortfolioAnalysisSharePanel analysisId={7} assets={analysisAssets} />);
    fireEvent.click(view.getByRole('button', { name: '의견 받기' }));

    expect(await view.findByText(comment.content)).toBeTruthy();
    fireEvent.click(view.getByRole('button', { name: `${pastShare.question} 공유 중단` }));
    expect(await view.findByText('공유를 중단했어요. 받은 의견은 유지됩니다.')).toBeTruthy();
    expect(fetchMock).toHaveBeenCalledWith(
      `/api/users/me/portfolio/analysis/shares/${pastShare.id}/revoke`,
      expect.objectContaining({ method: 'POST' }),
    );
  });

  it('비회원은 민감정보 없이 공유 결과를 보고 로그인 후 의견 작성을 선택한다', async () => {
    const fetchMock = vi.fn(() => Promise.resolve(response(publicShare)));
    vi.stubGlobal('fetch', fetchMock);

    const view = render(
      <MemoryRouter initialEntries={['/portfolio/analysis/share#raw-share-token']}>
        <AuthContext.Provider value={{ isLoggedIn: false, loading: false, role: null }}>
          <PortfolioAnalysisSharePage />
        </AuthContext.Provider>
      </MemoryRouter>,
    );

    expect(await view.findByRole('heading', { name: ownerShare.question })).toBeTruthy();
    expect(view.getAllByText('삼성전자').length).toBeGreaterThan(0);
    expect(view.queryByRole('heading', { name: 'AI 포트폴리오 분석' })).toBeNull();
    expect(view.getByText(/일부 종목만 공개되어/)).toBeTruthy();
    expect(view.queryByText('평균 매수가')).toBeNull();
    expect(view.getByText('수익률')).toBeTruthy();
    expect(view.queryByText(/보유 수량/)).toBeNull();
    expect(view.getByText('로그인하고 의견을 나눠보세요.')).toBeTruthy();
    expect(view.queryByRole('textbox', { name: '새 댓글' })).toBeNull();
    const login = view.getByRole('link', { name: '로그인하고 의견 남기기' });
    expect(login.getAttribute('href')).toBe(
      `/login?returnTo=${encodeURIComponent('/portfolio/analysis/share#raw-share-token')}`,
    );
    const source = view.getByRole('link', { name: '반도체 산업 동향' });
    expect(source.getAttribute('rel')).toBe('noreferrer');
    expect(source.getAttribute('referrerpolicy')).toBe('no-referrer');
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/portfolio/analysis/shares',
      expect.objectContaining({
        headers: { 'X-Portfolio-Share-Token': 'raw-share-token' },
      }),
    );
  });

  it('로그인 회원은 댓글을 저장하고 대댓글을 남긴다', async () => {
    const fetchMock = vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input);
      if (url.endsWith('/api/portfolio/analysis/shares') && !init?.method)
        return Promise.resolve(response({ ...publicShare, canComment: true }));
      if (url.endsWith('/api/auth/csrf'))
        return Promise.resolve(response({ headerName: 'X-XSRF-TOKEN', token: 'csrf' }));
      if (url.endsWith('/comments') && init?.method === 'POST')
        return Promise.resolve(response(comment));
      if (url.endsWith(`/comments/${comment.id}/replies`) && init?.method === 'POST')
        return Promise.resolve(
          response({
            ...comment,
            replies: [
              {
                id: 31,
                authorNickname: '리뷰어',
                content: '작성자 답변을 확인하고 비중 조절을 다시 생각해 볼게요.',
                fromOwner: false,
                editable: true,
                createdAt: '2026-11-12T02:00:00Z',
                updatedAt: '2026-11-12T02:00:00Z',
              },
            ],
          }),
        );
      throw new Error(`unexpected request: ${url}`);
    });
    vi.stubGlobal('fetch', fetchMock);

    const view = render(
      <MemoryRouter initialEntries={['/portfolio/analysis/share#raw-share-token']}>
        <AuthContext.Provider
          value={{ isLoggedIn: true, memberId: 22, loading: false, role: 'USER' }}
        >
          <PortfolioAnalysisSharePage />
        </AuthContext.Provider>
      </MemoryRouter>,
    );

    const textarea = await view.findByRole('textbox', { name: '새 댓글' });
    expect(view.queryByRole('link', { name: '의견 남기기' })).toBeNull();
    fireEvent.change(textarea, { target: { value: '한' } });
    expect(view.getByText('의견은 2자 이상 입력해 주세요.')).toBeTruthy();
    expect(view.getByRole('button', { name: '의견 남기기' }).hasAttribute('disabled')).toBe(true);
    fireEvent.change(textarea, { target: { value: '<의견>' } });
    expect(view.getByText('< 또는 > 문자는 사용할 수 없어요.')).toBeTruthy();
    fireEvent.change(textarea, { target: { value: comment.content } });
    fireEvent.click(view.getByRole('button', { name: '의견 남기기' }));

    await waitFor(() => expect(view.getByRole('button', { name: '수정' })).toBeTruthy());
    expect(view.getByText('댓글을 남겼어요.')).toBeTruthy();
    fireEvent.change(textarea, { target: { value: '새로운 의견' } });
    expect(view.queryByText('댓글을 남겼어요.')).toBeNull();
    fireEvent.click(view.getByRole('button', { name: '수정' }));
    fireEvent.change(view.getByRole('textbox', { name: '댓글 수정' }), {
      target: { value: '<수정>' },
    });
    expect(view.getByText('< 또는 > 문자는 사용할 수 없어요.')).toBeTruthy();
    expect(view.getByRole('button', { name: '수정 저장' }).hasAttribute('disabled')).toBe(true);
    fireEvent.click(view.getByRole('button', { name: '취소' }));
    const reply = '작성자 답변을 확인하고 비중 조절을 다시 생각해 볼게요.';
    fireEvent.click(view.getByRole('button', { name: '답글 달기' }));
    fireEvent.change(view.getByRole('textbox', { name: '리뷰어님 댓글에 답글' }), {
      target: { value: reply },
    });
    fireEvent.click(view.getByRole('button', { name: '답글 등록' }));
    expect(await view.findByText(reply)).toBeTruthy();
  });

  it('댓글을 남길 수 없는 회원에게 작성 폼 대신 의견 안내를 표시한다', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(() => Promise.resolve(response(publicShare))),
    );
    const view = render(
      <MemoryRouter initialEntries={['/portfolio/analysis/share#raw-share-token']}>
        <AuthContext.Provider
          value={{ isLoggedIn: true, memberId: 22, loading: false, role: 'USER' }}
        >
          <PortfolioAnalysisSharePage />
        </AuthContext.Provider>
      </MemoryRouter>,
    );

    expect(await view.findByText(/이 링크에서는 의견을 남길 수 없어요/)).toBeTruthy();
    expect(view.queryByRole('textbox', { name: '새 댓글' })).toBeNull();
  });
});
