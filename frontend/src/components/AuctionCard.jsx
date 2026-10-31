import React, { useState, useEffect } from 'react';
import { Clock, ArrowUpRight, ShieldCheck, Flame } from 'lucide-react';
import { formatCurrency, getCountdown } from '../utils/formatters';

export default function AuctionCard({ auction, onSelectAuction }) {
  const [countdown, setCountdown] = useState(() => getCountdown(auction.endTime));

  useEffect(() => {
    // Tick every second for live countdown
    const timer = setInterval(() => {
      setCountdown(getCountdown(auction.endTime));
    }, 1000);

    return () => clearInterval(timer);
  }, [auction.endTime]);

  const isClosed = auction.status === 'CLOSED' || countdown.isEnded;
  const currentBid = auction.currentHighestBid || auction.startingPrice;
  const placeholderImg = `https://images.unsplash.com/photo-1526304640581-d334cdbbf45e?auto=format&fit=crop&w=600&q=80`;

  return (
    <div className="auction-card" onClick={() => onSelectAuction(auction.id)} style={{ cursor: 'pointer' }}>
      {/* Image with Badges */}
      <div className="auction-image-wrap">
        <img
          src={auction.imageUrl || placeholderImg}
          alt={auction.title}
          className="auction-image"
          onError={(e) => {
            e.target.onerror = null;
            e.target.src = placeholderImg;
          }}
        />

        <div className="auction-badges">
          {/* Status Badge */}
          {isClosed ? (
            <span className="badge badge-ended">Closed</span>
          ) : countdown.isUrgent ? (
            <span className="badge badge-urgent">
              <Flame size={12} /> Ending Soon
            </span>
          ) : (
            <span className="badge badge-active">Live</span>
          )}

          {/* Real-time Countdown Timer */}
          <div className="timer-pill">
            <Clock size={12} />
            <span>{countdown.text}</span>
          </div>
        </div>
      </div>

      {/* Card Body */}
      <div className="auction-card-body">
        <h3 className="auction-card-title">{auction.title}</h3>
        <p className="auction-card-desc">{auction.description || 'No description provided.'}</p>

        {/* Pricing Row */}
        <div className="price-row">
          <div>
            <div className="price-label">Current Bid</div>
            <div className="price-value">{formatCurrency(currentBid)}</div>
          </div>

          <div style={{ textAlign: 'right' }}>
            <div className="price-label">Starting Price</div>
            <div className="price-start">{formatCurrency(auction.startingPrice)}</div>
          </div>
        </div>

        {/* Action Button */}
        <button
          className={`btn ${isClosed ? 'btn-secondary' : 'btn-emerald'} btn-sm`}
          style={{ width: '100%', marginTop: '0.85rem' }}
          onClick={(e) => {
            e.stopPropagation();
            onSelectAuction(auction.id);
          }}
        >
          <span>{isClosed ? 'View Audit Trail' : 'Enter Bidding Floor'}</span>
          <ArrowUpRight size={14} />
        </button>
      </div>
    </div>
  );
}

