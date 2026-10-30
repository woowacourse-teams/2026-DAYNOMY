import { trackLoadResult } from '../news/issue/analytics';
import { useEffect, useState } from 'react';
import { getFeaturedPeople } from './api';
import type { FeaturedPerson } from './types';

export function useFeaturedPeople(surface = 'people', page = 1, size = 6, personId?: number) {
  const [people, setPeople] = useState<FeaturedPerson[]>([]);
  const [pageInfo, setPageInfo] = useState({
    page: 1,
    size,
    totalElements: 0,
    totalPages: 0,
    hasNext: false,
  });
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [attempt, setAttempt] = useState(0);
  useEffect(() => {
    const startedAt = performance.now();
    const controller = new AbortController();
    setLoading(true);
    setError('');
    getFeaturedPeople(page, size, personId, controller.signal)
      .then(({ people: result, ...resultPageInfo }) => {
        if (!controller.signal.aborted) {
          setPeople(result);
          setPageInfo(resultPageInfo);
          trackLoadResult(
            surface,
            'people',
            startedAt,
            'success',
            result.reduce((sum, person) => sum + person.posts.length, 0),
            attempt,
          );
        }
      })
      .catch(() => {
        if (!controller.signal.aborted) {
          setError('인물의 글을 불러오지 못했습니다.');
          trackLoadResult(surface, 'people', startedAt, 'error', 0, attempt);
        }
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });
    return () => controller.abort();
  }, [attempt, page, personId, size, surface]);
  return {
    people,
    ...pageInfo,
    loading,
    error,
    retry: () => setAttempt((value) => value + 1),
  };
}
