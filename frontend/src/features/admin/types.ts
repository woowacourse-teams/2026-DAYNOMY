import type { Category } from '../news/newslist/types';

export type AdminNewsStatus = 'DRAFT' | 'PUBLISHED' | 'REJECTED' | 'DELETED';
export type AdminNewsSource = {
  name: string;
  url: string;
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
};

export type AdminNewsFilterStatus = AdminNewsStatus | 'ALL';
export type AdminNewsFilterCategory = Category | 'ALL';

export type AdminAssetRankingSyncResponse = {
  savedCount: number;
};
