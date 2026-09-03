import { createContext } from 'react';

export type MemberRole = 'USER' | 'ADMIN';

export type AuthContextValue = {
  isLoggedIn: boolean;
  loading: boolean;
  role: MemberRole | null;
};

export const AuthContext = createContext<AuthContextValue | null>(null);
