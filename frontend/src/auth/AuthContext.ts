import { createContext } from 'react';

export type MemberRole = 'USER' | 'ADMIN';

export type AuthContextValue = {
  isLoggedIn: boolean;
  loading: boolean;
  role: MemberRole | null;
  nickname?: string | null;
  clearSession?: () => void;
  refreshSession?: () => void;
  updateNickname?: (nickname: string) => void;
};

export const AuthContext = createContext<AuthContextValue | null>(null);
