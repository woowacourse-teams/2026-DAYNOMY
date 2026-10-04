import type { Category } from '../news/newslist/types';

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
};

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
};

export type AdminNewsFormValues = {
  title: string;
  content: string;
  sources: AdminNewsSource[];
  category: Category | '';
  imageSelection?: AdminWikimediaImageSelection | null;
};

export type AdminNewsFilterStatus = AdminNewsStatus | 'ALL';
export type AdminNewsFilterCategory = Category | 'ALL';

export type AdminAssetRankingSyncResponse = {
  savedCount: number;
};

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
  baseDate: string;
  receivedCount: number;
  createdCount: number;
  updatedCount: number;
  skippedCount: number;
};
