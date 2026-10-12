import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react';
import { getMyProfile } from '../features/pages/api';
import { AuthContext, type MemberRole } from './AuthContext';

export function AuthProvider({ children }: { children: ReactNode }) {
  const [isLoggedIn, setIsLoggedIn] = useState(false);
  const [loading, setLoading] = useState(true);
  const [role, setRole] = useState<MemberRole | null>(null);
  const [nickname, setNickname] = useState<string | null>(null);
  const [sessionVersion, setSessionVersion] = useState(0);
  const refreshSession = useCallback(() => {
    setLoading(true);
    setSessionVersion((value) => value + 1);
  }, []);
  const clearSession = useCallback(() => {
    setIsLoggedIn(false);
    setRole(null);
    setNickname(null);
  }, []);

  useEffect(() => {
    const controller = new AbortController();

    getMyProfile(controller.signal)
      .then(({ role: profileRole, nickname: profileNickname }) => {
        if (!controller.signal.aborted) {
          setIsLoggedIn(true);
          setRole(profileRole);
          setNickname(profileNickname);
        }
      })
      .catch(() => {
        if (!controller.signal.aborted) {
          setIsLoggedIn(false);
          setRole(null);
          setNickname(null);
        }
      })
      .finally(() => {
        if (!controller.signal.aborted) {
          setLoading(false);
        }
      });

    return () => controller.abort();
  }, [sessionVersion]);

  const value = useMemo(
    () => ({
      isLoggedIn,
      loading,
      role,
      nickname,
      clearSession,
      refreshSession,
      updateNickname: setNickname,
    }),
    [isLoggedIn, loading, nickname, role, clearSession, refreshSession],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
