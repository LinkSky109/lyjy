import { createContext, useCallback, useContext, useState, type ReactNode } from 'react';
import { clearStoredAuth, getStoredAuth, storeAuth } from '../api/http';
import type { AuthState } from '../api/types';

interface AuthContextValue {
  session: AuthState | null;
  login: (auth: AuthState) => void;
  logout: () => void;
}

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [session, setSession] = useState<AuthState | null>(() => getStoredAuth());

  const login = useCallback((auth: AuthState) => {
    storeAuth(auth);
    setSession(auth);
  }, []);

  const logout = useCallback(() => {
    clearStoredAuth();
    setSession(null);
  }, []);

  return (
    <AuthContext.Provider value={{ session, login, logout }}>{children}</AuthContext.Provider>
  );
}

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within AuthProvider');
  }
  return context;
}
