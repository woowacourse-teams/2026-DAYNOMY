import { createContext } from 'react';

export type AuthContextValue = {
  isLoggedIn: boolean;
  loading: boolean;
};

export const AuthContext = createContext<AuthContextValue | null>(null);
