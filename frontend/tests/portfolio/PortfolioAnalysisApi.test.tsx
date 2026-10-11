import { afterEach, describe, expect, it, vi } from 'vitest';
import { analyzePortfolio } from '../../src/features/portfolio/api';

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'content-type': 'application/json' },
  });
}

const analysisResponse = {
  totalAssetCount: 2,
  analyzedAssetCount: 2,
  overallDirection: 'POSITIVE',
  overallScore: 61,
  positiveImpactScore: 70,
  negativeImpactScore: 9,
  analyzedAt: '2026-10-05T08:00:00Z',
  overallImpact: '반도체 관련 이슈가 포트폴리오에 영향을 줄 수 있어요.',
  impacts: [
    {
      assetName: '삼성전자',
      weight: 60,
      direction: 'POSITIVE',
      impactLevel: 'HIGH',
      issueSummary: '반도체 수요가 증가했어요.',
      expectedReaction: '긍정적인 반응이 예상돼요.',
      outlook: '수요 흐름을 확인해야 해요.',
      reason: '실적 개선 가능성이 있어요.',
      evidenceSentence: '반도체 수요가 전년보다 증가했어요.',
      sources: [{ title: '반도체 산업 동향', url: 'https://example.com/semiconductor' }],
      confirmedFact: {
        summary: '반도체 수요가 증가했어요.',
        evidenceSentence: '반도체 수요가 전년보다 증가했어요.',
        sources: [{ title: '반도체 산업 동향', url: 'https://example.com/semiconductor' }],
      },
      marketOpinion: { summary: '장기 수요를 긍정적으로 보는 의견이 있어요.' },
      comprehensiveAnalysis: {
        summary: '실적 개선 가능성이 있어요.',
        reasoningSteps: ['반도체 사업', '수요 증가', '실적 개선 가능'],
        portfolioImpact: '보유 비중이 높아 전체 영향이 클 수 있어요.',
        counterScenario: '수요가 감소하면 영향이 달라질 수 있어요.',
        nextIndicator: '메모리 가격을 확인해야 해요.',
      },
      relatedVideos: [
        {
          title: '반도체 시장 전망',
          url: 'https://www.youtube.com/watch?v=video1',
          channelTitle: '경제 채널',
          publishedAt: '2026-10-05T00:00:00Z',
          thumbnailUrl: 'https://example.com/video1.jpg',
        },
      ],
      rank: 1,
    },
    {
      assetName: 'SK하이닉스',
      weight: 40,
      direction: 'NEUTRAL',
      impactLevel: 'LOW',
      issueSummary: '직접적인 관련 이슈가 확인되지 않았어요.',
      expectedReaction: '중립적인 반응이 예상돼요.',
      outlook: '추가 흐름을 지켜봐야 해요.',
      reason: '직접적인 근거가 부족해요.',
      evidenceSentence: '직접적인 관련 이슈가 확인되지 않았어요.',
      sources: [],
      confirmedFact: {
        summary: '직접적인 관련 이슈가 확인되지 않았어요.',
        evidenceSentence: '직접적인 관련 이슈가 확인되지 않았어요.',
        sources: [],
      },
      marketOpinion: { summary: '시장 의견을 확인하지 못했어요.' },
      comprehensiveAnalysis: {
        summary: '중립적인 반응이 예상돼요.',
        reasoningSteps: ['메모리 사업', '관련 이슈 없음', '영향 제한적'],
        portfolioImpact: '현재 확인된 영향은 제한적이에요.',
        counterScenario: '새로운 이슈가 발생하면 영향이 달라질 수 있어요.',
        nextIndicator: '메모리 가격을 확인해야 해요.',
      },
      relatedVideos: [],
      rank: 2,
    },
  ],
  sources: [{ title: '반도체 산업 동향', url: 'https://example.com/semiconductor' }],
};

afterEach(() => {
  vi.restoreAllMocks();
  vi.unstubAllGlobals();
});

describe('포트폴리오 대시보드 분석 API', () => {
  it('전체 보유 자산과 비중으로 포트폴리오 분석을 요청한다', async () => {
    const fetchMock = vi.fn(async () => jsonResponse(analysisResponse));
    vi.stubGlobal('fetch', fetchMock);
    const assets = [
      { assetName: '삼성전자', weight: 60 },
      { assetName: 'SK하이닉스', weight: 40 },
    ];

    await expect(analyzePortfolio(assets)).resolves.toEqual(analysisResponse);
    expect(fetchMock).toHaveBeenCalledWith(expect.stringContaining('/api/portfolio/analysis'), {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ assets }),
      signal: undefined,
      credentials: 'include',
    });
  });

  it('자산별 출처가 누락된 잘못된 응답을 거부한다', async () => {
    const invalidResponse = {
      ...analysisResponse,
      impacts: analysisResponse.impacts.map(({ sources: _sources, ...impact }) => impact),
    };
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => jsonResponse(invalidResponse)),
    );

    await expect(analyzePortfolio([{ assetName: '삼성전자', weight: 100 }])).rejects.toThrow(
      '포트폴리오 분석 API 응답 형식이 올바르지 않습니다.',
    );
  });
});
