import { useCallback, useEffect, useMemo, useRef, useState, type ReactNode } from 'react';
import { getMyProfile } from '../features/pages/api';
import { AuthContext, type MemberRole } from './AuthContext';

export function AuthProvider({ children }: { children: ReactNode }) {
  const profileRequest = useRef<AbortController | null>(null);
  const [memberId, setMemberId] = useState<number | null>(null);
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
    profileRequest.current?.abort();
    setLoading(false);
    setMemberId(null);
    setIsLoggedIn(false);
    setRole(null);
    setNickname(null);
  }, []);

  useEffect(() => {
    const controller = new AbortController();
    profileRequest.current = controller;

    getMyProfile(controller.signal)
      .then(({ id, role: profileRole, nickname: profileNickname }) => {
        if (!controller.signal.aborted) {
          setMemberId(id);
          setIsLoggedIn(true);
          setRole(profileRole);
          setNickname(profileNickname);
        }
      })
      .catch(() => {
        if (!controller.signal.aborted) {
          setMemberId(null);
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
      memberId,
      isLoggedIn,
      loading,
      role,
      nickname,
      clearSession,
      refreshSession,
      updateNickname: setNickname,
    }),
    [memberId, isLoggedIn, loading, nickname, role, clearSession, refreshSession],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
