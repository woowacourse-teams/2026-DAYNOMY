import { NEWS_CATEGORIES } from '../news/newslist/constants';
import { CATEGORY_LABELS } from '../news/newslist/types';
import type { Category } from '../news/newslist/types';
import type { AdminNewsFilterCategory, AdminNewsFilterStatus } from './types';

export const ADMIN_NEWS_CATEGORIES = NEWS_CATEGORIES.filter(
  (option): option is { label: string; value: Category } => option.value !== 'ALL',
);

export const ADMIN_NEWS_STATUS_OPTIONS: Array<{
  label: string;
  value: AdminNewsFilterStatus;
}> = [
  { label: '전체 상태', value: 'ALL' },
  { label: '초안', value: 'DRAFT' },
  { label: '발행됨', value: 'PUBLISHED' },
  { label: '반려됨', value: 'REJECTED' },
  { label: '삭제됨', value: 'DELETED' },
];

export const ADMIN_NEWS_CATEGORY_OPTIONS: Array<{
  label: string;
  value: AdminNewsFilterCategory;
}> = [{ label: '전체 카테고리', value: 'ALL' }, ...ADMIN_NEWS_CATEGORIES];

export const NEWS_STATUS_LABELS = {
  DRAFT: '초안',
  PUBLISHED: '발행됨',
  REJECTED: '반려됨',
  DELETED: '삭제됨',
} as const;

export { CATEGORY_LABELS };
