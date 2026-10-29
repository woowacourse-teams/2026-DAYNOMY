export type FeedbackCategory = 'BUG' | 'INCONVENIENCE' | 'FEATURE_REQUEST';
export type FeedbackStatus = 'PENDING' | 'IN_PROGRESS' | 'RESOLVED';

export type AdminFeedback = {
  id: number;
  content: string;
  pagePath: string;
  category: FeedbackCategory | null;
  authorMemberId: number | null;
  authorNickname: string | null;
  status: FeedbackStatus;
  createdAt: string;
  updatedAt: string;
};

export type AdminFeedbackPage = {
  items: AdminFeedback[];
  page: number;
  size: number;
  totalPages: number;
  totalElements: number;
  hasNext: boolean;
};
