import { useCallback, useEffect, useRef, useState, type ReactNode } from 'react';
import { generateAdminNewsImageCandidate } from './api';
import {
  AdminNewsImageGenerationContext,
  type AdminNewsAiImageCandidate,
  type AdminNewsImageGenerationState,
} from './AdminNewsImageGenerationContext';

const initialState: AdminNewsImageGenerationState = {
  newsId: null,
  status: 'idle',
  candidates: [],
  error: null,
};

export function AdminNewsImageGenerationProvider({ children }: { children: ReactNode }) {
  const [state, setState] = useState(initialState);
  const stateRef = useRef(state);
  const generating = useRef(false);

  const replaceState = useCallback((nextState: AdminNewsImageGenerationState) => {
    stateRef.current = nextState;
    setState(nextState);
  }, []);

  const startGeneration = useCallback(
    async (newsId: number) => {
      if (generating.current) return;

      const previous = stateRef.current;
      if (previous.newsId !== newsId && previous.candidates.length > 0) return;
      generating.current = true;

      const candidates = previous.newsId === newsId ? previous.candidates : [];
      if (previous.newsId !== newsId) {
        previous.candidates.forEach((candidate) => URL.revokeObjectURL(candidate.previewUrl));
      }
      replaceState({ newsId, status: 'loading', candidates, error: null });

      try {
        const image = await generateAdminNewsImageCandidate(newsId);
        const candidate = { image, previewUrl: URL.createObjectURL(image) };
        const current = stateRef.current;
        replaceState({
          newsId,
          status: 'success',
          candidates: [...current.candidates, candidate],
          error: null,
        });
      } catch (error) {
        const current = stateRef.current;
        replaceState({
          ...current,
          newsId,
          status: 'error',
          error: error instanceof Error ? error.message : 'AI 이미지 후보를 생성하지 못했습니다.',
        });
      } finally {
        generating.current = false;
      }
    },
    [replaceState],
  );

  const removeCandidate = useCallback(
    (candidate: AdminNewsAiImageCandidate) => {
      const current = stateRef.current;
      if (current.status === 'loading') return;
      URL.revokeObjectURL(candidate.previewUrl);
      const candidates = current.candidates.filter((item) => item !== candidate);
      replaceState(candidates.length === 0 ? initialState : { ...current, candidates });
    },
    [replaceState],
  );

  const clearCandidates = useCallback(() => {
    stateRef.current.candidates.forEach((candidate) => URL.revokeObjectURL(candidate.previewUrl));
    replaceState(initialState);
  }, [replaceState]);

  useEffect(() => {
    if (state.status !== 'loading') return;
    const warnBeforeUnload = (event: BeforeUnloadEvent) => {
      event.preventDefault();
      event.returnValue = '';
    };
    window.addEventListener('beforeunload', warnBeforeUnload);
    return () => window.removeEventListener('beforeunload', warnBeforeUnload);
  }, [state.status]);

  useEffect(
    () => () => {
      stateRef.current.candidates.forEach((candidate) => URL.revokeObjectURL(candidate.previewUrl));
    },
    [],
  );

  return (
    <AdminNewsImageGenerationContext.Provider
      value={{ ...state, startGeneration, removeCandidate, clearCandidates }}
    >
      {children}
    </AdminNewsImageGenerationContext.Provider>
  );
}
