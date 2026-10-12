export type FeedbackStatus = 'PENDING' | 'RESOLVED';

export type AdminFeedback = {
  id: number;
  content: string;
  pagePath: string;
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
