import type { NewsDetailPayload } from '../newsdetail/types';

export const SAMPLE_NEWS_ID = 'sample-today';
export const showSampleNews = import.meta.env?.DEV && import.meta.env.MODE !== 'test';

export function getSampleNews(): NewsDetailPayload {
  return {
    news: {
      id: -1,
      title: '[샘플] 오늘의 경제, 세 가지 흐름으로 살펴보기',
      category: 'ECONOMY',
      publishedAt: new Date().toISOString(),
      imageUrl: new URL('../../../assets/news-home-banner.jpg', import.meta.url).href,
      content: [
        '화면 확인을 위해 만든 샘플 기사입니다. 실제 오늘의 뉴스나 시장 상황을 보도한 내용이 아닙니다.',
        '경제 뉴스를 읽을 때는 금리, 물가, 환율이라는 세 가지 흐름을 함께 살펴볼 수 있습니다. 이 샘플에서는 기사 이미지와 제목, 본문이 어떻게 표시되는지 확인합니다.',
        '첫 번째 문단에서는 이슈의 배경을 소개하고, 두 번째 문단에서는 주요 내용을 풀어 설명합니다. 마지막 문단에는 앞으로 확인할 내용을 담아 독자가 기사 흐름을 따라갈 수 있도록 구성합니다.',
      ],
      sources: [],
    },
    keywords: [],
    marketAnalysis: { status: 'empty' },
  };
}
