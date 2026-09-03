import type { Category } from '../news/newslist/types';

export type AdminNewsStatus = 'DRAFT' | 'PUBLISHED' | 'REJECTED' | 'DELETED';
export type AdminNewsSource = 'DART' | 'KOSIS' | 'BOK';

export type AdminNewsListItemResponse = {
  id: number;
  title: string;
  description: string | null;
  imageUrl: string | null;
  source: AdminNewsSource | null;
  sourceUrl: string;
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
  description: string | null;
  imageUrl: string | null;
  source: AdminNewsSource | null;
  sourceUrl: string;
  category: Category;
  publishedAt: string | null;
  status: AdminNewsStatus;
};

export type AdminNewsFormValues = {
  title: string;
  content: string;
  description: string;
  sourceUrl: string;
  category: Category | '';
};

export type AdminNewsFilterStatus = AdminNewsStatus | 'ALL';
export type AdminNewsFilterCategory = Category | 'ALL';
