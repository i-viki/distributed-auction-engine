import React, { useState, useEffect } from 'react';
import { Gavel, Radio, Bell, PlusCircle, User, LogOut, Cpu, FileText } from 'lucide-react';
import { useAuth } from '../context/AuthContext';
import { wsService } from '../services/websocketService';
import { api } from '../api/apiClient';

export default function Navbar({
  onOpenCreate,
  onOpenAuth,
  onOpenArchitecture,
  onOpenNotifications,
  onNavigateHome,
}) {
  const { user, logout, isAuthenticated } = useAuth();
  const [wsStatus, setWsStatus] = useState('DISCONNECTED');
  const [unreadCount, setUnreadCount] = useState(0);

  useEffect(() => {
    // Monitor WebSocket connection state
    const unsubscribe = wsService.onStatusChange((status) => {
      setWsStatus(status);
    });

    return () => unsubscribe();
  }, []);

  useEffect(() => {
    if (isAuthenticated) {
      loadNotifications();
      const interval = setInterval(loadNotifications, 15000);
      return () => clearInterval(interval);
    } else {
      setUnreadCount(0);
    }
  }, [isAuthenticated]);

  const loadNotifications = async () => {
    try {
      const data = await api.getNotifications();
      if (Array.isArray(data)) {
        setUnreadCount(data.length);
      }
    } catch {
      // ignore
    }
  };

  return (
    <header className="navbar">
      <div className="container navbar-inner">
        {/* Brand / Logo */}
        <div className="brand" onClick={onNavigateHome}>
          <div className="brand-icon">
            <Gavel size={20} />
          </div>
          <div>
            <span className="brand-title">LiveBid Sequencer</span>
            <span className="brand-tag">Distributed</span>
          </div>
        </div>

        {/* Action Controls */}
        <div className="nav-actions">
          {/* WebSocket Status Indicator */}
          <div className="status-pill" title={`STOMP Gateway: ${wsStatus}`}>
            <span className={`status-dot ${wsStatus === 'CONNECTED' ? 'connected' : ''}`} />
            <span>{wsStatus === 'CONNECTED' ? 'STOMP Live' : wsStatus}</span>
          </div>

          {/* Architecture & Docs */}
          <button
            className="btn btn-secondary btn-sm"
            onClick={onOpenArchitecture}
            title="System Architecture & Concurrency Specs"
          >
            <Cpu size={15} />
            <span>Architecture</span>
          </button>

          {/* Create Auction */}
          <button
            className="btn btn-primary btn-sm"
            onClick={onOpenCreate}
            title="Publish New Auction"
          >
            <PlusCircle size={15} />
            <span>Create Auction</span>
          </button>

          {/* Notifications Bell */}
          {isAuthenticated && (
            <button
              className="btn btn-secondary btn-sm"
              onClick={onOpenNotifications}
              title="Notifications"
              style={{ position: 'relative' }}
            >
              <Bell size={16} />
              {unreadCount > 0 && (
                <span
                  style={{
                    position: 'absolute',
                    top: '-4px',
                    right: '-4px',
                    background: '#ef4444',
                    color: 'white',
                    fontSize: '0.65rem',
                    fontWeight: 700,
                    borderRadius: '9999px',
                    padding: '0.1rem 0.35rem',
                    lineHeight: 1,
                  }}
                >
                  {unreadCount}
                </span>
              )}
            </button>
          )}

          {/* User Auth Section */}
          {isAuthenticated ? (
            <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
              <div
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: '0.4rem',
                  background: 'rgba(30, 41, 59, 0.6)',
                  padding: '0.35rem 0.75rem',
                  borderRadius: 'var(--radius-md)',
                  border: '1px solid var(--border-subtle)',
                  fontSize: '0.85rem',
                }}
              >
                <User size={14} color="#a5b4fc" />
                <span style={{ fontWeight: 600 }}>{user.name}</span>
                <span style={{ fontSize: '0.7rem', color: 'var(--text-dim)' }}>(ID: {user.id})</span>
              </div>
              <button
                className="btn btn-secondary btn-sm"
                onClick={logout}
                title="Logout"
              >
                <LogOut size={14} />
              </button>
            </div>
          ) : (
            <button className="btn btn-primary btn-sm" onClick={onOpenAuth}>
              <User size={15} />
              <span>Sign In / Demo</span>
            </button>
          )}
        </div>
      </div>
    </header>
  );
}

