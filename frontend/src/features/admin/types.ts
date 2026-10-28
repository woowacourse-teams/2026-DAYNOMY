import type { Category } from '../news/newslist/types';
import type { StockSearchItem } from '../stocks/types';

export type AdminNewsStatus = 'DRAFT' | 'PUBLISHED' | 'REJECTED' | 'DELETED';
export type AdminNewsSource = {
  name: string;
  url: string;
};

export type AdminNewsImageSource = {
  name: string;
  url: string;
  author: string;
  license: string;
  licenseUrl: string;
  type: AdminNewsImageSourceType;
};

export type AdminNewsImageSourceType = 'NONE' | 'AI_GENERATED' | 'WIKIMEDIA' | 'MANUAL';

export type AdminWikimediaImageSelection = {
  title: string;
};

export type AdminWikimediaImageCandidate = {
  title: string;
  thumbnailUrl: string;
  sourceUrl: string;
  author: string;
  license: string;
  licenseUrl: string;
  width: number;
  height: number;
};

export type AdminNewsListItemResponse = {
  id: number;
  title: string;
  imageUrl: string | null;
  sources: AdminNewsSource[];
  category: Category;
  publishedAt: string | null;
  status: AdminNewsStatus;
  createdAt: string;
};

export type AdminNewsPageResponse = {
  items: AdminNewsListItemResponse[];
  page: number;
  size: number;
  totalPages: number;
  totalElements: number;
  hasNext: boolean;
};

export type AdminNewsResponse = {
  id: number;
  title: string;
  content: string;
  imageUrl: string | null;
  imageSource?: AdminNewsImageSource;
  sources: AdminNewsSource[];
  category: Category;
  publishedAt: string | null;
  status: AdminNewsStatus;
  relatedAssets?: StockSearchItem[];
};

export type AdminNewsFormValues = {
  title: string;
  content: string;
  sources: AdminNewsSource[];
  category: Category | '';
  imageSelection?: AdminWikimediaImageSelection | null;
  assetIds?: number[];
};

export type AdminNewsFilterStatus = AdminNewsStatus | 'ALL';
export type AdminNewsFilterCategory = Category | 'ALL';

export type AdminNewsGenerationResponse = {
  savedCount: number;
};

export type AdminStockSyncResponse = {
  baseDate: string;
  syncedCount: number;
  createdCount: number;
  updatedCount: number;
  delistedCount: number;
};

export type AdminStockPriceSyncResponse = {
  baseDate: string | null;
  receivedCount: number;
  createdCount: number;
  updatedCount: number;
  skippedCount: number;
  synchronizedDateCount: number;
  pendingDateCount: number;
  failedDateCount: number;
};

export type AdminStockPriceBackfillResponse = {
  from: string;
  to: string;
  synchronizedDateCount: number;
  receivedCount: number;
  createdCount: number;
  updatedCount: number;
  skippedCount: number;
};

export type AdminLeagueRecalculationResponse = {
  calculatedCount: number;
};

export type ContentCollectionSourceType = 'YOUTUBE' | 'THREADS' | 'X';

export type AdminContentCollectionSource = {
  id: number;
  name: string;
  sourceType: ContentCollectionSourceType;
  sourceUrl: string;
  assetId: number | null;
  assetName: string | null;
  enabled: boolean;
  lastCollectedAt: string | null;
};

export type AdminContentCollectionSourceRequest = {
  name: string;
  sourceType: ContentCollectionSourceType;
  sourceUrl: string;
  assetId: number | null;
  enabled: boolean;
};

export type AdminContentCollectionManualRequest = {
  url: string;
  title: string | null;
  description: string | null;
  publishedDate: string | null;
};

export type AdminContentCollectionResult = {
  sourceId: number;
  fetchedCount: number;
  savedCount: number;
  duplicateCount: number;
  linkedCount: number;
};

export type AdminContentCollectionBatchResult = {
  sourceCount: number;
  successCount: number;
  failureCount: number;
  fetchedCount: number;
  savedCount: number;
  duplicateCount: number;
  linkedCount: number;
};

export type AdminContentCollectionReview = {
  id: number;
  sourceName: string;
  sourceType: ContentCollectionSourceType;
  title: string;
  description: string | null;
  url: string;
  imageUrl: string | null;
  publishedDate: string | null;
  suggestedAssets: AdminContentCollectionReviewAsset[];
  createdAt: string;
};

export type AdminContentCollectionReviewAsset = {
  id: number;
  name: string;
  assetCode: string;
};

export type AdminContentCollectionReviewPage = {
  items: AdminContentCollectionReview[];
  page: number;
  size: number;
  totalPages: number;
  totalElements: number;
  hasNext: boolean;
};

export type AdminContentCollectionRematchResult = {
  processedCount: number;
  matchedCount: number;
  suggestionCount: number;
};
