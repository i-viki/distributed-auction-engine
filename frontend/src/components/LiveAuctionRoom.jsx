import React, { useState, useEffect, useRef } from 'react';
import {
  ArrowLeft,
  Clock,
  Zap,
  TrendingUp,
  Shield,
  Bot,
  User,
  AlertCircle,
  AlertTriangle,
  CheckCircle,
  Radio,
  Flame,
} from 'lucide-react';
import { api } from '../api/apiClient';
import { wsService } from '../services/websocketService';
import { useAuth } from '../context/AuthContext';
import { formatCurrency, formatDateTime, getCountdown } from '../utils/formatters';

export default function LiveAuctionRoom({ auctionId, onBack, onOpenAuth }) {
  const { user, isAuthenticated } = useAuth();

  const [auction, setAuction] = useState(null);
  const [bids, setBids] = useState([]);
  const [loading, setLoading] = useState(true);
  const [submittingBid, setSubmittingBid] = useState(false);
  const [bidAmount, setBidAmount] = useState('');
  const [errorMessage, setErrorMessage] = useState(null);
  const [successMessage, setSuccessMessage] = useState(null);
  const [priceFlash, setPriceFlash] = useState(false);
  const [outbidAlert, setOutbidAlert] = useState(null);

  // Auto-bid state
  const [autoBidMax, setAutoBidMax] = useState('');
  const [autoBidActive, setAutoBidActive] = useState(false);
  const [autoBidLoading, setAutoBidLoading] = useState(false);

  // Countdown timer state
  const [countdown, setCountdown] = useState({ isEnded: false, text: '—' });

  // Load initial auction data and bid history
  const loadAuctionData = async () => {
    try {
      setLoading(true);
      const [auctionData, bidsData] = await Promise.all([
        api.getAuctionById(auctionId),
        api.getBidHistory(auctionId),
      ]);
      setAuction(auctionData);
      setBids(Array.isArray(bidsData) ? bidsData : []);
      setCountdown(getCountdown(auctionData.endTime));
    } catch (err) {
      setErrorMessage(err.message || 'Failed to load auction data.');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadAuctionData();
  }, [auctionId]);

  // Live Timer Countdown Loop
  useEffect(() => {
    if (!auction?.endTime) return;
    const interval = setInterval(() => {
      const cd = getCountdown(auction.endTime);
      setCountdown(cd);
      if (cd.isEnded && auction.status === 'ACTIVE') {
        setAuction((prev) => (prev ? { ...prev, status: 'CLOSED' } : prev));
      }
    }, 1000);
    return () => clearInterval(interval);
  }, [auction?.endTime, auction?.status]);

  // Real-Time STOMP WebSocket Subscription
  useEffect(() => {
    if (!auctionId) return;

    // Connect to WebSocket if not already active
    wsService.init();

    const unsubscribe = wsService.subscribeToAuction(auctionId, (bidUpdate) => {
      console.log('[LiveAuctionRoom] Received WebSocket Bid Update:', bidUpdate);

      // Trigger visual price flash
      setPriceFlash(true);
      setTimeout(() => setPriceFlash(false), 800);

      // Update auction state & trigger instant outbid detection
      setAuction((prev) => {
        if (!prev) return prev;

        // Check if current logged-in user was leading and is now outbid
        if (
          user &&
          prev.currentHighestBidderId &&
          Number(prev.currentHighestBidderId) === Number(user.id) &&
          Number(bidUpdate.bidderId) !== Number(user.id)
        ) {
          setOutbidAlert({
            bidderId: bidUpdate.bidderId,
            amount: bidUpdate.amount,
          });
        } else if (user && Number(bidUpdate.bidderId) === Number(user.id)) {
          setOutbidAlert(null); // Clear alert when user takes the lead
        }

        return {
          ...prev,
          currentHighestBid: bidUpdate.currentHighestBid || bidUpdate.amount,
          currentHighestBidderId: bidUpdate.currentHighestBidderId || bidUpdate.bidderId,
          endTime: bidUpdate.endTime || prev.endTime,
        };
      });

      // Prepend to bids list if not already present
      setBids((prev) => {
        const exists = prev.some((b) => b.id === bidUpdate.bidId);
        if (exists) return prev;
        const newBid = {
          id: bidUpdate.bidId,
          auctionId: bidUpdate.auctionId,
          bidderId: bidUpdate.bidderId,
          amount: bidUpdate.amount,
          placedAt: bidUpdate.placedAt || new Date().toISOString(),
          autoBid: bidUpdate.autoBid,
        };
        return [newBid, ...prev];
      });
    });

    return () => {
      unsubscribe();
    };
  }, [auctionId]);

  // Handle Manual Bidding
  const handlePlaceBid = async (amountToPlace) => {
    setErrorMessage(null);
    setSuccessMessage(null);

    if (!isAuthenticated) {
      onOpenAuth();
      return;
    }

    const value = parseFloat(amountToPlace);
    if (!value || isNaN(value) || value <= 0) {
      setErrorMessage('Please enter a valid positive bid amount.');
      return;
    }

    const currentHighest = parseFloat(auction?.currentHighestBid || auction?.startingPrice || 0);
    if (value <= currentHighest) {
      setErrorMessage(`Bid must be strictly higher than current price of ${formatCurrency(currentHighest)}.`);
      return;
    }

    setSubmittingBid(true);
    try {
      const placed = await api.placeBid(auctionId, value);
      setSuccessMessage(`Bid of ${formatCurrency(value)} accepted and sequenced!`);
      setBidAmount('');
      // Optimistically update local view
      setAuction((prev) => ({
        ...prev,
        currentHighestBid: placed.amount,
        currentHighestBidderId: placed.bidderId,
      }));
    } catch (err) {
      setErrorMessage(err.message || 'Failed to place bid. Optimistic lock contention occurred.');
    } finally {
      setSubmittingBid(false);
    }
  };

  // Quick Bid Increment
  const handleQuickIncrement = (increment) => {
    const currentHighest = parseFloat(auction?.currentHighestBid || auction?.startingPrice || 0);
    const nextAmount = (currentHighest + increment).toFixed(2);
    setBidAmount(nextAmount);
    handlePlaceBid(nextAmount);
  };

  // Auto-Bid Bot Configuration
  const handleSetAutoBid = async (e) => {
    e.preventDefault();
    setErrorMessage(null);
    setSuccessMessage(null);

    if (!isAuthenticated) {
      onOpenAuth();
      return;
    }

    const maxVal = parseFloat(autoBidMax);
    if (!maxVal || maxVal <= 0) {
      setErrorMessage('Please enter a valid auto-bid maximum threshold.');
      return;
    }

    setAutoBidLoading(true);
    try {
      await api.setAutoBid(auctionId, maxVal);
      setAutoBidActive(true);
      setSuccessMessage(`Auto-Bid bot activated with ceiling ${formatCurrency(maxVal)}!`);
    } catch (err) {
      setErrorMessage(err.message || 'Failed to activate auto-bid bot.');
    } finally {
      setAutoBidLoading(false);
    }
  };

  const handleDeactivateAutoBid = async () => {
    setAutoBidLoading(true);
    try {
      await api.deactivateAutoBid(auctionId);
      setAutoBidActive(false);
      setAutoBidMax('');
      setSuccessMessage('Auto-Bid bot deactivated.');
    } catch (err) {
      setErrorMessage(err.message || 'Failed to deactivate auto-bid bot.');
    } finally {
      setAutoBidLoading(false);
    }
  };

  if (loading) {
    return (
      <div className="container" style={{ padding: '4rem 0', textAlign: 'center' }}>
        <div className="status-dot connected" style={{ margin: '0 auto 1rem', width: '16px', height: '16px' }} />
        <p style={{ color: 'var(--text-muted)' }}>Connecting to Live Bidding Floor...</p>
      </div>
    );
  }

  if (!auction) {
    return (
      <div className="container" style={{ padding: '4rem 0', textAlign: 'center' }}>
        <h2>Auction Not Found</h2>
        <button className="btn btn-secondary" onClick={onBack} style={{ marginTop: '1rem' }}>
          Back to Floor
        </button>
      </div>
    );
  }

  const currentPrice = auction.currentHighestBid || auction.startingPrice;
  const isLeader = isAuthenticated && user && Number(auction.currentHighestBidderId) === Number(user.id);
  const isClosed = auction.status === 'CLOSED' || countdown.isEnded;
  const isScheduled = auction.status === 'SCHEDULED' && !isClosed;
  const isLive = auction.status === 'LIVE' && !isClosed;

  return (
    <div className="container bidding-floor">
      {/* Navigation Header */}
      <div className="floor-header">
        <button className="back-link" onClick={onBack}>
          <ArrowLeft size={16} />
          <span>Back to Auctions</span>
        </button>

        <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem' }}>
          <div className="status-pill">
            <Radio size={14} color="#10b981" />
            <span>STOMP Stream Active</span>
          </div>

          {countdown.isUrgent && !isClosed && (
            <span className="badge badge-urgent">
              <Flame size={12} /> Sniper Window Active
            </span>
          )}
        </div>
      </div>

      {/* Main Floor Grid: Left Console, Right Audit Stream */}
      <div className="floor-grid">
        {/* Left Column: Live Pricing, Bidding & Bot Controls */}
        <div className="floor-main">
          {/* Header Card */}
          <div style={{ background: 'var(--bg-secondary)', padding: '1.5rem', borderRadius: 'var(--radius-lg)', border: '1px solid var(--border-subtle)' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', flexWrap: 'wrap', gap: '1rem' }}>
              <div>
                <h1 style={{ fontSize: '1.75rem', fontWeight: 800, marginBottom: '0.4rem' }}>{auction.title}</h1>
                <p style={{ color: 'var(--text-muted)', fontSize: '0.95rem', maxWidth: '650px' }}>
                  {auction.description || 'No description.'}
                </p>
              </div>

              {/* Status and Countdown */}
              <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'flex-end', gap: '0.4rem' }}>
                <span className={`badge ${isClosed ? 'badge-ended' : isScheduled ? 'badge-scheduled' : 'badge-active'}`}>
                  {isClosed ? 'Auction Closed' : isScheduled ? 'Scheduled' : 'Live Bidding'}
                </span>
                <div className="timer-pill" style={{ fontSize: '0.85rem' }}>
                  <Clock size={14} />
                  <span>{isScheduled ? `Starts: ${formatDateTime(auction.startTime)}` : countdown.text}</span>
                </div>
              </div>
            </div>
          </div>

          {/* Instant Outbid Alert Banner */}
          {outbidAlert && !isClosed && (
            <div
              style={{
                background: 'linear-gradient(135deg, rgba(239, 68, 68, 0.25), rgba(245, 158, 11, 0.15))',
                border: '1px solid rgba(239, 68, 68, 0.6)',
                borderRadius: 'var(--radius-lg)',
                padding: '1rem 1.25rem',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'space-between',
                flexWrap: 'wrap',
                gap: '0.75rem',
                boxShadow: '0 0 20px rgba(239, 68, 68, 0.25)',
              }}
            >
              <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem' }}>
                <div style={{ background: 'rgba(239, 68, 68, 0.2)', padding: '0.4rem', borderRadius: '50%' }}>
                  <AlertTriangle size={24} color="#ef4444" />
                </div>
                <div>
                  <div style={{ fontWeight: 800, fontSize: '1rem', color: '#ffffff', letterSpacing: '-0.01em' }}>
                    YOU WERE JUST OUTBID!
                  </div>
                  <div style={{ fontSize: '0.85rem', color: '#fca5a5' }}>
                    Bidder #{outbidAlert.bidderId} placed a higher bid of <strong>{formatCurrency(outbidAlert.amount)}</strong>.
                  </div>
                </div>
              </div>

              <button
                className="btn btn-emerald btn-sm"
                onClick={() => handleQuickIncrement(50)}
                style={{ fontWeight: 700 }}
              >
                <Zap size={14} />
                <span>Reclaim Lead (+ $50)</span>
              </button>
            </div>
          )}

          {/* Big Live Price Display Banner */}
          <div className={`live-price-banner ${priceFlash ? 'flash' : ''}`}>
            <div className="live-price-row">
              <div>
                <div className="price-label" style={{ marginBottom: '0.25rem' }}>
                  Current Highest Bid
                </div>
                <div className="live-amount">{formatCurrency(currentPrice)}</div>
              </div>

              <div style={{ textAlign: 'right' }}>
                <div className="price-label" style={{ marginBottom: '0.4rem' }}>
                  Leading Bidder
                </div>
                {auction.currentHighestBidderId ? (
                  <div className={`leader-pill ${isLeader ? 'you' : ''}`}>
                    <User size={14} />
                    <span>{isLeader ? 'You are leading!' : `Bidder #${auction.currentHighestBidderId}`}</span>
                  </div>
                ) : (
                  <div className="leader-pill">
                    <span>Starting Price ({formatCurrency(auction.startingPrice)})</span>
                  </div>
                )}
              </div>
            </div>
          </div>

          {/* Feedback Alerts */}
          {errorMessage && (
            <div className="form-error">
              <AlertCircle size={16} />
              <span>{errorMessage}</span>
            </div>
          )}

          {successMessage && (
            <div
              style={{
                background: 'rgba(16, 185, 129, 0.15)',
                border: '1px solid rgba(16, 185, 129, 0.4)',
                color: '#6ee7b7',
                padding: '0.75rem 1rem',
                borderRadius: 'var(--radius-md)',
                display: 'flex',
                alignItems: 'center',
                gap: '0.5rem',
                fontSize: '0.9rem',
              }}
            >
              <CheckCircle size={16} />
              <span>{successMessage}</span>
            </div>
          )}

          {/* Manual Bidding Console */}
          {isLive ? (
            <div className="bidding-box">
              <div className="bidding-box-title">
                <Zap size={18} color="#f59e0b" />
                <span>Place Live Bid</span>
                <span style={{ fontSize: '0.75rem', color: 'var(--text-dim)', fontWeight: 400, marginLeft: 'auto' }}>
                  Optimistic Lock Sequenced
                </span>
              </div>

              {/* Quick Increment Buttons */}
              <div style={{ marginBottom: '0.65rem', fontSize: '0.8rem', color: 'var(--text-muted)' }}>
                One-Click Quick Bids:
              </div>
              <div className="quick-bids">
                {[10, 50, 100, 250].map((inc) => (
                  <button
                    key={inc}
                    className="quick-bid-btn"
                    onClick={() => handleQuickIncrement(inc)}
                    disabled={submittingBid}
                  >
                    +{formatCurrency(inc)}
                  </button>
                ))}
              </div>

              {/* Custom Bid Input */}
              <form
                onSubmit={(e) => {
                  e.preventDefault();
                  handlePlaceBid(bidAmount);
                }}
                className="custom-bid-form"
              >
                <input
                  type="number"
                  step="0.01"
                  min="0.01"
                  className="input-field"
                  placeholder={`Enter amount > ${formatCurrency(currentPrice)}`}
                  value={bidAmount}
                  onChange={(e) => setBidAmount(e.target.value)}
                  disabled={submittingBid}
                />
                <button
                  type="submit"
                  className="btn btn-emerald btn-lg"
                  disabled={submittingBid}
                  style={{ minWidth: '150px' }}
                >
                  <TrendingUp size={18} />
                  <span>{submittingBid ? 'Sequencing...' : 'Submit Bid'}</span>
                </button>
              </form>
            </div>
          ) : isScheduled ? (
            <div
              style={{
                background: 'var(--bg-secondary)',
                border: '1px solid var(--border-subtle)',
                borderRadius: 'var(--radius-lg)',
                padding: '2rem',
                textAlign: 'center',
              }}
            >
              <h3 style={{ fontSize: '1.25rem', marginBottom: '0.5rem', color: '#f59e0b' }}>
                Auction Scheduled
              </h3>
              <p style={{ color: 'var(--text-muted)' }}>
                This auction is scheduled to start at <strong>{formatDateTime(auction.startTime)}</strong>. Bidding will open automatically when live.
              </p>
            </div>
          ) : (
            <div
              style={{
                background: 'var(--bg-secondary)',
                border: '1px solid var(--border-subtle)',
                borderRadius: 'var(--radius-lg)',
                padding: '2rem',
                textAlign: 'center',
              }}
            >
              <h3 style={{ fontSize: '1.25rem', marginBottom: '0.5rem', color: 'var(--text-main)' }}>
                This Auction Has Concluded
              </h3>
              <p style={{ color: 'var(--text-muted)' }}>
                Final winning bid: <strong>{formatCurrency(currentPrice)}</strong> by{' '}
                <strong>
                  {auction.currentHighestBidderId ? `Bidder #${auction.currentHighestBidderId}` : 'None'}
                </strong>
              </p>
            </div>
          )}

          {/* Auto-Bid Bot Panel */}
          {!isClosed && (
            <div className="bot-card">
              <div className="bot-card-header">
                <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                  <Bot size={20} color="#f59e0b" />
                  <span style={{ fontWeight: 700, fontSize: '1rem' }}>Proxy Auto-Bid Bot</span>
                </div>
                <span className="bot-tag">Sub-millisecond</span>
              </div>
              <p style={{ fontSize: '0.85rem', color: 'var(--text-muted)', marginBottom: '1rem' }}>
                Set a ceiling limit. The engine will automatically counter competing sniper bids on your behalf up to your max threshold.
              </p>

              {autoBidActive ? (
                <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', color: '#6ee7b7' }}>
                    <CheckCircle size={16} />
                    <span>Auto-bid active up to <strong>{formatCurrency(autoBidMax)}</strong></span>
                  </div>
                  <button
                    className="btn btn-danger btn-sm"
                    onClick={handleDeactivateAutoBid}
                    disabled={autoBidLoading}
                  >
                    Deactivate Bot
                  </button>
                </div>
              ) : (
                <form onSubmit={handleSetAutoBid} style={{ display: 'flex', gap: '0.75rem' }}>
                  <input
                    type="number"
                    step="0.01"
                    min="0.01"
                    className="input-field"
                    placeholder="Maximum ceiling amount ($)..."
                    value={autoBidMax}
                    onChange={(e) => setAutoBidMax(e.target.value)}
                    disabled={autoBidLoading}
                  />
                  <button
                    type="submit"
                    className="btn btn-primary"
                    disabled={autoBidLoading}
                    style={{ minWidth: '130px' }}
                  >
                    <Bot size={16} />
                    <span>Deploy Bot</span>
                  </button>
                </form>
              )}
            </div>
          )}
        </div>

        {/* Right Column: Live Bid Feed / Audit Log */}
        <div className="floor-sidebar">
          <div className="feed-panel">
            <div className="feed-header">
              <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                <TrendingUp size={16} color="#10b981" />
                <span style={{ fontWeight: 700, fontSize: '0.95rem' }}>Live Bid Ticker</span>
              </div>
              <span style={{ fontSize: '0.75rem', color: 'var(--text-dim)' }}>
                {bids.length} Bids
              </span>
            </div>

            {/* Scrolling Feed */}
            <div className="feed-stream">
              {bids.length === 0 ? (
                <div style={{ padding: '2rem 1rem', textAlign: 'center', color: 'var(--text-dim)', fontSize: '0.85rem' }}>
                  No bids sequenced yet. Be the first to bid!
                </div>
              ) : (
                bids.map((b, idx) => {
                  const isCurrentHighest = idx === 0;
                  return (
                    <div key={b.id || idx} className={`feed-item ${isCurrentHighest ? 'highlight' : ''}`}>
                      <div>
                        <div className="feed-item-amount">{formatCurrency(b.amount)}</div>
                        <div className="feed-item-meta">
                          Bidder #{b.bidderId}
                          {user && Number(b.bidderId) === Number(user.id) && ' (You)'}
                        </div>
                      </div>

                      <div style={{ textAlign: 'right' }}>
                        {b.autoBid && <span className="bot-tag" style={{ fontSize: '0.65rem' }}>BOT</span>}
                        <div className="feed-item-meta" style={{ marginTop: '0.2rem' }}>
                          {formatDateTime(b.placedAt)}
                        </div>
                      </div>
                    </div>
                  );
                })
              )}
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}

