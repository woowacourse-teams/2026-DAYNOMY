import { useContext } from 'react';
import { AuthContext } from '../auth/AuthContext';

export function useAuth() {
  const context = useContext(AuthContext);

  if (!context) {
    throw new Error('useAuth must be used within AuthProvider.');
  }

  return context;
}

export function useLoginStatus() {
  return useAuth().isLoggedIn;
}
