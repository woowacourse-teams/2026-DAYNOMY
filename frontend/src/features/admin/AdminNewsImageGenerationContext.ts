import { createContext, useContext } from 'react';

export type AdminNewsAiImageCandidate = { image: Blob; previewUrl: string };
export type AdminNewsImageGenerationState = {
  newsId: number | null;
  status: 'idle' | 'loading' | 'success' | 'error';
  candidates: AdminNewsAiImageCandidate[];
  error: string | null;
};

export type AdminNewsImageGenerationContextValue = AdminNewsImageGenerationState & {
  startGeneration: (newsId: number) => Promise<void>;
  removeCandidate: (candidate: AdminNewsAiImageCandidate) => void;
  clearCandidates: () => void;
};

export const AdminNewsImageGenerationContext =
  createContext<AdminNewsImageGenerationContextValue | null>(null);

export function useAdminNewsImageGeneration() {
  const context = useContext(AdminNewsImageGenerationContext);
  if (!context) {
    throw new Error('AdminNewsImageGenerationProvider가 필요합니다.');
  }
  return context;
}
