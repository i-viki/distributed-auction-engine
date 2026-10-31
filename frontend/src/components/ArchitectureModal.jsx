import React from 'react';
import { X, Cpu, Layers, ShieldCheck, Zap, Radio, ExternalLink, Globe } from 'lucide-react';

export default function ArchitectureModal({ isOpen, onClose }) {
  if (!isOpen) return null;

  return (
    <div className="modal-overlay" onClick={onClose}>
      <div className="modal-content" style={{ maxWidth: '680px' }} onClick={(e) => e.stopPropagation()}>
        <div className="modal-header">
          <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
            <Cpu size={20} color="#6366f1" />
            <h2 className="modal-title">System Architecture & Concurrency Specs</h2>
          </div>
          <button className="modal-close" onClick={onClose}>
            <X size={18} />
          </button>
        </div>

        <div className="modal-body" style={{ maxHeight: '70vh', overflowY: 'auto' }}>
          {/* Author Badge */}
          <div
            style={{
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
              background: 'rgba(16, 185, 129, 0.1)',
              border: '1px solid rgba(16, 185, 129, 0.3)',
              borderRadius: 'var(--radius-md)',
              padding: '0.75rem 1rem',
              marginBottom: '1.25rem',
            }}
          >
            <div>
              <div style={{ fontSize: '0.85rem', fontWeight: 700, color: '#6ee7b7' }}>
                Engineered by Jayavignesh
              </div>
              <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>
                Distributed Systems & Real-Time Financial Engineering
              </div>
            </div>
            <a
              href="https://jayavignesh.dev"
              target="_blank"
              rel="noreferrer"
              className="btn btn-secondary btn-sm"
              style={{ display: 'flex', alignItems: 'center', gap: '0.35rem' }}
            >
              <Globe size={13} />
              <span>jayavignesh.dev</span>
            </a>
          </div>

          {/* Core Technical Highlights */}
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '1rem', marginBottom: '1.25rem' }}>
            <div style={{ background: 'var(--bg-surface)', padding: '1rem', borderRadius: 'var(--radius-md)', border: '1px solid var(--border-subtle)' }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: '0.4rem', color: '#a5b4fc', fontWeight: 700, fontSize: '0.9rem', marginBottom: '0.4rem' }}>
                <ShieldCheck size={16} />
                <span>Optimistic MVCC</span>
              </div>
              <p style={{ fontSize: '0.8rem', color: 'var(--text-muted)', lineHeight: 1.4 }}>
                Uses database <code>version</code> column with 3-tier backoff retry to eliminate race conditions under peak sniper bidding.
              </p>
            </div>

            <div style={{ background: 'var(--bg-surface)', padding: '1rem', borderRadius: 'var(--radius-md)', border: '1px solid var(--border-subtle)' }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: '0.4rem', color: '#6ee7b7', fontWeight: 700, fontSize: '0.9rem', marginBottom: '0.4rem' }}>
                <Radio size={16} />
                <span>STOMP / WebSocket</span>
              </div>
              <p style={{ fontSize: '0.8rem', color: 'var(--text-muted)', lineHeight: 1.4 }}>
                Subscribes to <code>/topic/auctions/{'{id}'}</code>, broadcasting accepted bids to thousands of concurrent clients in real-time.
              </p>
            </div>
          </div>

          {/* Interactive Swagger Docs Link */}
          <div
            style={{
              background: 'linear-gradient(135deg, rgba(99, 102, 241, 0.15), rgba(30, 41, 59, 0.5))',
              border: '1px solid rgba(99, 102, 241, 0.3)',
              borderRadius: 'var(--radius-md)',
              padding: '1rem',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
              marginBottom: '1.25rem',
            }}
          >
            <div>
              <div style={{ fontWeight: 700, fontSize: '0.95rem' }}>OpenAPI 3.0 & Swagger UI</div>
              <div style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}>
                Test all REST endpoints and schemas directly in the live interactive console.
              </div>
            </div>
            <a
              href="http://localhost:8080/swagger-ui.html"
              target="_blank"
              rel="noreferrer"
              className="btn btn-primary btn-sm"
              style={{ display: 'inline-flex', alignItems: 'center', gap: '0.4rem' }}
            >
              <span>Swagger UI</span>
              <ExternalLink size={14} />
            </a>
          </div>

          {/* Distributed Roadmap */}
          <h4 style={{ fontSize: '0.9rem', textTransform: 'uppercase', letterSpacing: '0.05em', color: 'var(--text-dim)', marginBottom: '0.75rem' }}>
            Distributed Scaling Roadmap (v0.1.0 → v1.0.0)
          </h4>
          <ol style={{ paddingLeft: '1.25rem', fontSize: '0.85rem', color: 'var(--text-muted)', lineHeight: 1.6 }}>
            <li>
              <strong>Stage 1 (Current):</strong> In-memory Spring Boot container with JDBC optimistic locking and STOMP message broker.
            </li>
            <li>
              <strong>Stage 2:</strong> Redis In-Memory Sequencer executing atomic Lua evaluation scripts (<code>EVAL</code>) for sub-millisecond acceptance.
            </li>
            <li>
              <strong>Stage 3:</strong> Redis Pub/Sub cluster channel federation for horizontally scaled WebSocket gateways.
            </li>
            <li>
              <strong>Stage 4:</strong> Redis Sorted Set (<code>ZSET</code>) delay queue for millisecond-precision auction expirations.
            </li>
          </ol>
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

