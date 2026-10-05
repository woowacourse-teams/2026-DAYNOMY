export type StockRelatedContentSource = 'YOUTUBE' | 'THREADS' | 'INTERNAL_NEWS' | 'OTHER';

export type StockRelatedContent = {
  id: number;
  assetId: number;
  sourceType: StockRelatedContentSource;
  title: string;
  url: string;
  imageUrl: string | null;
  createdAt: string | null;
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

export type StockRelatedContentRequest = {
  sourceType: StockRelatedContentSource;
  title: string;
  url: string;
  imageUrl?: string | null;
};

export const STOCK_CONTENT_SOURCE_LABELS: Record<StockRelatedContentSource, string> = {
  YOUTUBE: 'YouTube',
  THREADS: 'Threads',
  INTERNAL_NEWS: 'DAYNOMY 뉴스',
  OTHER: '기타',
};
