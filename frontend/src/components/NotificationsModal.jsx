import React, { useState, useEffect } from 'react';
import { X, Bell, AlertTriangle, Trophy, CheckCircle, RefreshCw } from 'lucide-react';
import { api } from '../api/apiClient';
import { formatDateTime } from '../utils/formatters';

export default function NotificationsModal({ isOpen, onClose, onSelectAuction }) {
  const [notifications, setNotifications] = useState([]);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    if (isOpen) {
      loadNotifications();
    }
  }, [isOpen]);

  const loadNotifications = async () => {
    setLoading(true);
    try {
      const data = await api.getNotifications();
      setNotifications(Array.isArray(data) ? data : []);
    } catch (err) {
      console.error(err);
    } finally {
      setLoading(false);
    }
  };

  if (!isOpen) return null;

  return (
    <div className="modal-overlay" onClick={onClose}>
      <div className="modal-content" onClick={(e) => e.stopPropagation()}>
        <div className="modal-header">
          <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
            <Bell size={18} color="#f59e0b" />
            <h2 className="modal-title">Live Notifications & Alerts</h2>
          </div>
          <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
            <button className="btn btn-secondary btn-sm" onClick={loadNotifications} disabled={loading}>
              <RefreshCw size={13} className={loading ? 'spin' : ''} />
            </button>
            <button className="modal-close" onClick={onClose}>
              <X size={18} />
            </button>
          </div>
        </div>

        <div className="modal-body" style={{ maxHeight: '60vh', overflowY: 'auto' }}>
          {notifications.length === 0 ? (
            <div style={{ textAlign: 'center', padding: '2.5rem 1rem', color: 'var(--text-dim)' }}>
              <Bell size={32} style={{ marginBottom: '0.75rem', opacity: 0.5 }} />
              <p>No new notifications. You are all caught up!</p>
            </div>
          ) : (
            <div style={{ display: 'flex', flexDirection: 'column', gap: '0.75rem' }}>
              {notifications.map((n) => {
                const isOutbid = n.type === 'OUTBID';
                const isWon = n.type === 'WON';
                return (
                  <div
                    key={n.id}
                    style={{
                      background: 'var(--bg-surface)',
                      border: '1px solid var(--border-subtle)',
                      borderRadius: 'var(--radius-md)',
                      padding: '0.85rem 1rem',
                      display: 'flex',
                      alignItems: 'flex-start',
                      gap: '0.75rem',
                      cursor: n.auctionId ? 'pointer' : 'default',
                    }}
                    onClick={() => {
                      if (n.auctionId) {
                        onSelectAuction(n.auctionId);
                        onClose();
                      }
                    }}
                  >
                    <div style={{ marginTop: '0.15rem' }}>
                      {isOutbid ? (
                        <AlertTriangle size={18} color="#ef4444" />
                      ) : isWon ? (
                        <Trophy size={18} color="#f59e0b" />
                      ) : (
                        <CheckCircle size={18} color="#10b981" />
                      )}
                    </div>

                    <div style={{ flexGrow: 1 }}>
                      <div style={{ fontSize: '0.9rem', fontWeight: 600, color: 'var(--text-main)' }}>
                        {isOutbid
                          ? `You were outbid on "${n.auctionTitle || 'Auction #' + n.auctionId}"!`
                          : isWon
                          ? `Congratulations! You won "${n.auctionTitle || 'Auction #' + n.auctionId}"!`
                          : `Auction Update: ${n.auctionTitle || '#' + n.auctionId}`}
                      </div>
                      <div style={{ fontSize: '0.75rem', color: 'var(--text-dim)', marginTop: '0.2rem' }}>
                        {formatDateTime(n.sentAt)}
                      </div>
                    </div>
                  </div>
                );
              })}
            </div>
          )}
        </div>

        <div className="modal-footer">
          <button className="btn btn-secondary" onClick={onClose}>
            Close
          </button>
        </div>
      </div>
    </div>
  );
}

