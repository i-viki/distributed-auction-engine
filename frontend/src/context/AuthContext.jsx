import React, { createContext, useContext, useState, useEffect } from 'react';
import { api } from '../api/apiClient';

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [user, setUser] = useState(() => {
    const saved = localStorage.getItem('livebid_user');
    return saved ? JSON.parse(saved) : null;
  });
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    if (user?.token) {
      api.setToken(user.token);
    } else {
      api.setToken(null);
    }
  }, [user]);

  const login = async (email, password) => {
    setLoading(true);
    try {
      const res = await api.login(email, password);
      const userData = {
        id: res.userId,
        name: res.name,
        email: res.email,
        token: res.token,
      };
      setUser(userData);
      localStorage.setItem('livebid_user', JSON.stringify(userData));
      return userData;
    } finally {
      setLoading(false);
    }
  };

  const register = async (name, email, password) => {
    setLoading(true);
    try {
      await api.register(name, email, password);
      // Auto login after registration
      return await login(email, password);
    } finally {
      setLoading(false);
    }
  };

  const demoLogin = async (email, password, defaultName) => {
    setLoading(true);
    try {
      try {
        return await login(email, password);
      } catch (err) {
        // If login failed (e.g. user does not exist in fresh DB), auto-register
        await api.register(defaultName, email, password);
        return await login(email, password);
      }
    } finally {
      setLoading(false);
    }
  };

  const logout = () => {
    setUser(null);
    api.setToken(null);
    localStorage.removeItem('livebid_user');
    localStorage.removeItem('livebid_jwt');
  };

  return (
    <AuthContext.Provider value={{ user, login, register, demoLogin, logout, loading, isAuthenticated: !!user }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used within an AuthProvider');
  return ctx;
}

