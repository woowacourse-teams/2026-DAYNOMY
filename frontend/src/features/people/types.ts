export type SocialPlatform = 'THREADS' | 'X' | 'YOUTUBE';

export type FeaturedPersonPost = {
  id: number;
  sourceType: SocialPlatform;
  title: string;
  url: string;
  publishedAt?: string | null;
  createdAt: string | null;
};

export type FeaturedPerson = {
  id: number;
  name: string;
  xProfileUrl: string | null;
  threadsProfileUrl: string | null;
  youtubeProfileUrl: string | null;
  profileImageUrl: string | null;
  createdAt: string | null;
  posts: FeaturedPersonPost[];
};

export type FeaturedPeopleResponse = {
  people: FeaturedPerson[];
};

export type FeaturedPeoplePageResponse = FeaturedPeopleResponse & {
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  hasNext: boolean;
};

export type FeaturedPersonRequest = {
  name: string;
  xProfileUrl: string | null;
  threadsProfileUrl: string | null;
  youtubeProfileUrl: string | null;
  profileImageSelection?: { title: string } | null;
};

export type FeaturedPersonPostRequest = {
  sourceType: SocialPlatform;
  title: string | null;
  url: string;
  publishedAt?: string | null;
};

export type SocialPostSearchItem = {
  sourceType: SocialPlatform;
  title: string;
  url: string;
  publishedDate: string;
};

export type FeaturedPersonPostCandidate = {
  id: number;
  sourceType: SocialPlatform;
  title: string;
  url: string;
  publishedAt: string | null;
  status: 'PENDING';
  createdAt: string | null;
};

export type FeaturedPersonPostCandidateInboxItem = FeaturedPersonPostCandidate & {
  personId: number;
  personName: string;
  personProfileImageUrl: string | null;
};

export type FeaturedPersonPostCandidatePage = {
  candidates: FeaturedPersonPostCandidateInboxItem[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
};

export type FeaturedPersonPostCandidateBulkResponse = {
  succeeded: Array<{
    candidateId: number;
    post: FeaturedPersonPost | null;
  }>;
  failed: Array<{
    candidateId: number;
    errorCode: string;
    message: string;
  }>;
};

export function platformLabel(platform: SocialPlatform) {
  return platform === 'X' ? 'X' : platform === 'THREADS' ? 'Threads' : 'YouTube';
}
