import React, { createContext, useContext, useState, useEffect } from 'react';
import type { LoginResponse } from '@/types';

interface AuthContextType {
  session: LoginResponse | null;
  login: (data: LoginResponse) => void;
  logout: () => void;
  isAuthenticated: boolean;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

export const AuthProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [session, setSession] = useState<LoginResponse | null>(null);

  useEffect(() => {
    const stored = localStorage.getItem('boomerang_session');
    if (stored) {
      try {
        setSession(JSON.parse(stored));
      } catch (e) {
        localStorage.removeItem('boomerang_session');
      }
    }
  }, []);

  const login = (data: LoginResponse) => {
    localStorage.setItem('boomerang_session', JSON.stringify(data));
    setSession(data);
  };

  const logout = () => {
    localStorage.removeItem('boomerang_session');
    setSession(null);
  };

  return (
    <AuthContext.Provider
      value={{
        session,
        login,
        logout,
        isAuthenticated: !!session,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
};

export const useAuth = () => {
  const context = useContext(AuthContext);
  if (context === undefined) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
};
