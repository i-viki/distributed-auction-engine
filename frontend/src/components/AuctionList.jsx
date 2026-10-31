import React, { useState, useEffect } from 'react';
import { Search, Flame, RefreshCw, Zap, Shield, Activity, Plus } from 'lucide-react';
import AuctionCard from './AuctionCard';
import { api } from '../api/apiClient';

export default function AuctionList({ onSelectAuction, onOpenCreate }) {
  const [auctions, setAuctions] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  // Filters
  const [statusFilter, setStatusFilter] = useState('ALL');
  const [endingSoon, setEndingSoon] = useState(false);
  const [search, setSearch] = useState('');

  const fetchAuctions = async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await api.getAuctions({
        status: statusFilter === 'ALL' ? null : statusFilter,
        search: search.trim() || null,
        endingSoon,
        page: 0,
        size: 50,
      });
      setAuctions(Array.isArray(data) ? data : []);
    } catch (err) {
      setError(err.message || 'Failed to connect to Distributed Auction Engine API.');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchAuctions();
  }, [statusFilter, endingSoon]);

  const handleSearchSubmit = (e) => {
    e.preventDefault();
    fetchAuctions();
  };

  return (
    <div className="container" style={{ paddingBottom: '3rem' }}>
      {/* Hero Banner with Distributed Metrics */}
      <section className="hero-section">
        <div className="hero-banner">
          <h1 className="hero-title">Distributed Real-Time Auction Engine</h1>
          <p className="hero-subtitle">
            High-throughput, low-latency live auction sequencer engineered to handle extreme concurrency,
            eliminating race conditions during peak sniper bidding windows.
          </p>

          <div className="hero-metrics">
            <div className="metric-card">
              <div className="metric-icon indigo">
                <Zap size={20} />
              </div>
              <div>
                <div className="metric-value">{auctions.length}</div>
                <div className="metric-label">Auctions Registered</div>
              </div>
            </div>

            <div className="metric-card">
              <div className="metric-icon emerald">
                <Shield size={20} />
              </div>
              <div>
                <div className="metric-value">Optimistic Lock</div>
                <div className="metric-label">Contention Engine</div>
              </div>
            </div>

            <div className="metric-card">
              <div className="metric-icon amber">
                <Activity size={20} />
              </div>
              <div>
                <div className="metric-value">&lt; 5ms Target</div>
                <div className="metric-label">State Broadcast</div>
              </div>
            </div>
          </div>
        </div>
      </section>

      {/* Filter and Search Toolbar */}
      <div className="toolbar">
        {/* Status Chips */}
        <div className="filter-group">
          {['ALL', 'ACTIVE', 'CLOSED'].map((s) => (
            <button
              key={s}
              className={`filter-chip ${statusFilter === s ? 'active' : ''}`}
              onClick={() => setStatusFilter(s)}
            >
              {s === 'ALL' ? 'All Auctions' : s === 'ACTIVE' ? 'Live Only' : 'Closed'}
            </button>
          ))}

          <button
            className={`filter-chip ${endingSoon ? 'active' : ''}`}
            onClick={() => setEndingSoon(!endingSoon)}
            style={{ display: 'flex', alignItems: 'center', gap: '0.35rem' }}
          >
            <Flame size={14} color={endingSoon ? '#f59e0b' : 'inherit'} />
            <span>Ending Soon</span>
          </button>
        </div>

        {/* Search & Refresh */}
        <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem', flexWrap: 'wrap' }}>
          <form onSubmit={handleSearchSubmit} className="search-box">
            <Search size={15} className="search-icon" />
            <input
              type="text"
              className="search-input"
              placeholder="Search auctions..."
              value={search}
              onChange={(e) => setSearch(e.target.value)}
            />
          </form>

          <button
            className="btn btn-secondary btn-sm"
            onClick={fetchAuctions}
            title="Refresh Auctions"
            disabled={loading}
          >
            <RefreshCw size={14} className={loading ? 'spin' : ''} />
          </button>

          <button className="btn btn-primary btn-sm" onClick={onOpenCreate}>
            <Plus size={14} />
            <span>Create</span>
          </button>
        </div>
      </div>

      {/* Error Alert */}
      {error && (
        <div className="form-error">
          <span>{error}</span>
          <button className="btn btn-secondary btn-sm" onClick={fetchAuctions} style={{ marginLeft: 'auto' }}>
            Retry
          </button>
        </div>
      )}

      {/* Loading Skeleton */}
      {loading && auctions.length === 0 && (
        <div style={{ textAlign: 'center', padding: '4rem 0', color: 'var(--text-muted)' }}>
          <div className="status-dot connected" style={{ margin: '0 auto 1rem', width: '14px', height: '14px' }} />
          <p>Connecting to Auction Cluster & Loading Live Catalog...</p>
        </div>
      )}

      {/* Auctions Grid */}
      {!loading && auctions.length === 0 && (
        <div
          style={{
            textAlign: 'center',
            padding: '4rem 2rem',
            background: 'var(--bg-secondary)',
            borderRadius: 'var(--radius-lg)',
            border: '1px solid var(--border-subtle)',
          }}
        >
          <Zap size={36} color="#6366f1" style={{ marginBottom: '1rem' }} />
          <h3 style={{ fontSize: '1.25rem', marginBottom: '0.5rem' }}>No Auctions Found</h3>
          <p style={{ color: 'var(--text-muted)', marginBottom: '1.5rem', maxWidth: '400px', margin: '0 auto 1.5rem' }}>
            There are currently no auctions matching your active filters. Create a new auction to begin live bidding!
          </p>
          <button className="btn btn-primary" onClick={onOpenCreate}>
            <Plus size={16} />
            <span>Create New Auction</span>
          </button>
        </div>
      )}

      {auctions.length > 0 && (
        <div className="auction-grid">
          {auctions.map((auction) => (
            <AuctionCard
              key={auction.id}
              auction={auction}
              onSelectAuction={onSelectAuction}
            />
          ))}
        </div>
      )}
    </div>
  );
}

