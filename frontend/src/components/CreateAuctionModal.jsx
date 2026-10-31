import React, { useState } from 'react';
import { X, Plus, Image, Clock, DollarSign, AlertCircle } from 'lucide-react';
import { api } from '../api/apiClient';
import { useAuth } from '../context/AuthContext';

const PRESET_IMAGES = [
  {
    name: 'Vintage Watch',
    url: 'https://images.unsplash.com/photo-1523275335684-37898b6baf30?auto=format&fit=crop&w=800&q=80',
  },
  {
    name: 'Cyberpunk Sneaker',
    url: 'https://images.unsplash.com/photo-1552346154-21d32810aba3?auto=format&fit=crop&w=800&q=80',
  },
  {
    name: 'Classic Camera',
    url: 'https://images.unsplash.com/photo-1516035069371-29a1b244cc32?auto=format&fit=crop&w=800&q=80',
  },
  {
    name: 'Supercar',
    url: 'https://images.unsplash.com/photo-1503376780353-7e6692767b70?auto=format&fit=crop&w=800&q=80',
  },
];

export default function CreateAuctionModal({ isOpen, onClose, onAuctionCreated, onOpenAuth }) {
  const { isAuthenticated } = useAuth();

  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [imageUrl, setImageUrl] = useState(PRESET_IMAGES[0].url);
  const [startingPrice, setStartingPrice] = useState('100.00');
  const [durationMinutes, setDurationMinutes] = useState('10'); // default 10 minutes for fast testing
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState(null);

  if (!isOpen) return null;

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError(null);

    if (!isAuthenticated) {
      onClose();
      onOpenAuth();
      return;
    }

    if (!title.trim()) {
      setError('Title is required.');
      return;
    }

    const price = parseFloat(startingPrice);
    if (!price || price <= 0) {
      setError('Starting price must be greater than zero.');
      return;
    }

    const duration = parseInt(durationMinutes, 10) || 10;

    setSubmitting(true);
    try {
      const created = await api.createAuction({
        title: title.trim(),
        description: description.trim() || 'No description.',
        imageUrl: imageUrl.trim() || null,
        startingPrice: price,
        durationMinutes: duration,
      });

      onAuctionCreated(created);
      onClose();
    } catch (err) {
      setError(err.message || 'Failed to create auction.');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="modal-overlay" onClick={onClose}>
      <div className="modal-content" onClick={(e) => e.stopPropagation()}>
        <div className="modal-header">
          <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
            <Plus size={18} color="#6366f1" />
            <h2 className="modal-title">Create New Auction</h2>
          </div>
          <button className="modal-close" onClick={onClose}>
            <X size={18} />
          </button>
        </div>

        <form onSubmit={handleSubmit}>
          <div className="modal-body">
            {error && (
              <div className="form-error">
                <AlertCircle size={16} />
                <span>{error}</span>
              </div>
            )}

            <div className="form-group">
              <label className="form-label">Auction Title</label>
              <input
                type="text"
                className="input-field"
                placeholder="e.g. 1972 Rolex Submariner Ref. 5513"
                value={title}
                onChange={(e) => setTitle(e.target.value)}
                required
              />
            </div>

            <div className="form-group">
              <label className="form-label">Description</label>
              <textarea
                className="input-field"
                rows="2"
                placeholder="Details, provenance, condition..."
                value={description}
                onChange={(e) => setDescription(e.target.value)}
                style={{ resize: 'vertical' }}
              />
            </div>

            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '1rem' }}>
              <div className="form-group">
                <label className="form-label">Starting Price ($)</label>
                <input
                  type="number"
                  step="0.01"
                  min="0.01"
                  className="input-field"
                  value={startingPrice}
                  onChange={(e) => setStartingPrice(e.target.value)}
                  required
                />
              </div>

              <div className="form-group">
                <label className="form-label">Duration</label>
                <select
                  className="input-field"
                  value={durationMinutes}
                  onChange={(e) => setDurationMinutes(e.target.value)}
                >
                  <option value="2">2 Minutes (Rapid Test)</option>
                  <option value="5">5 Minutes (Live Demo)</option>
                  <option value="15">15 Minutes</option>
                  <option value="60">1 Hour</option>
                  <option value="1440">24 Hours</option>
                </select>
              </div>
            </div>

            {/* Image Presets */}
            <div className="form-group">
              <label className="form-label">Choose Showcase Image</label>
              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: '0.5rem', marginBottom: '0.5rem' }}>
                {PRESET_IMAGES.map((img) => (
                  <button
                    type="button"
                    key={img.name}
                    className="filter-chip"
                    style={{
                      borderColor: imageUrl === img.url ? 'var(--accent-indigo)' : 'transparent',
                      background: imageUrl === img.url ? 'rgba(99, 102, 241, 0.2)' : 'var(--bg-surface)',
                      fontSize: '0.75rem',
                      padding: '0.35rem 0.2rem',
                      textAlign: 'center',
                    }}
                    onClick={() => setImageUrl(img.url)}
                  >
                    {img.name}
                  </button>
                ))}
              </div>
              <input
                type="url"
                className="input-field"
                placeholder="Or paste custom image URL..."
                value={imageUrl}
                onChange={(e) => setImageUrl(e.target.value)}
              />
            </div>
          </div>

          <div className="modal-footer">
            <button type="button" className="btn btn-secondary" onClick={onClose} disabled={submitting}>
              Cancel
            </button>
            <button type="submit" className="btn btn-primary" disabled={submitting}>
              {submitting ? 'Publishing...' : 'Publish Auction'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}

