export type StockRelatedContentSource = 'YOUTUBE' | 'THREADS' | 'X' | 'INTERNAL_NEWS' | 'OTHER';

export type StockRelatedContent = {
  id: number;
  assetId: number;
  sourceType: StockRelatedContentSource;
  title: string;
  url: string;
  imageUrl: string | null;
  createdAt: string | null;
  publishedDate?: string | null;
};

export type StockRelatedContentsResponse = {
  assetId: number;
  assetCode: string;
  assetName: string;
  contents: StockRelatedContent[];
};

export type YouTubeSearchItem = {
  title: string;
  url: string;
  channelTitle: string;
  publishedAt: string;
  thumbnailUrl: string;
};

export type SocialPostSearchItem = {
  sourceType: 'THREADS' | 'X';
  title: string;
  url: string;
  publishedDate: string;
};

export type StockRelatedContentRequest = {
  sourceType: StockRelatedContentSource;
  title: string;
  url: string;
  imageUrl?: string | null;
  publishedDate?: string | null;
};

export const STOCK_CONTENT_SOURCE_LABELS: Record<StockRelatedContentSource, string> = {
  YOUTUBE: 'YouTube',
  THREADS: 'Threads',
  X: 'X',
  INTERNAL_NEWS: 'DAYNOMY 이슈',
  OTHER: '기타',
};
