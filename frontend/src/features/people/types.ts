export type SocialPlatform = 'THREADS' | 'X' | 'YOUTUBE';

export type FeaturedPersonPost = {
  id: number;
  sourceType: SocialPlatform;
  title: string;
  url: string;
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
};

export type SocialPostSearchItem = {
  sourceType: SocialPlatform;
  title: string;
  url: string;
  publishedDate: string;
};

export function platformLabel(platform: SocialPlatform) {
  return platform === 'X' ? 'X' : platform === 'THREADS' ? 'Threads' : 'YouTube';
}
