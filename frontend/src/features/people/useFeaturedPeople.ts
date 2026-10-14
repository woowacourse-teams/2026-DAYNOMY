import { trackLoadResult } from '../news/issue/analytics';
import { useEffect, useState } from 'react';
import { getFeaturedPeople } from './api';
import type { FeaturedPerson } from './types';

export function useFeaturedPeople(surface = 'people') {
  const [people, setPeople] = useState<FeaturedPerson[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [attempt, setAttempt] = useState(0);
  useEffect(() => {
    const startedAt = performance.now();
    const controller = new AbortController();
    setLoading(true);
    setError('');
    getFeaturedPeople(controller.signal)
      .then(({ people: result }) => {
        if (!controller.signal.aborted) {
          setPeople(result);
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
  }, [attempt, surface]);
  return { people, loading, error, retry: () => setAttempt((value) => value + 1) };
}
