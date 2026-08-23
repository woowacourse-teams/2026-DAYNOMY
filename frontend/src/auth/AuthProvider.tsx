import { useEffect, useMemo, useState, type ReactNode } from 'react';
import { getMyProfile } from '../features/pages/api';
import { AuthContext } from './AuthContext';

export function AuthProvider({ children }: { children: ReactNode }) {
  const [isLoggedIn, setIsLoggedIn] = useState(false);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const controller = new AbortController();

    getMyProfile(controller.signal)
      .then(() => {
        if (!controller.signal.aborted) {
          setIsLoggedIn(true);
        }
      })
      .catch(() => {
        if (!controller.signal.aborted) {
          setIsLoggedIn(false);
        }
      })
      .finally(() => {
        if (!controller.signal.aborted) {
          setLoading(false);
        }
      });

    return () => controller.abort();
  }, []);

  const value = useMemo(() => ({ isLoggedIn, loading }), [isLoggedIn, loading]);

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
