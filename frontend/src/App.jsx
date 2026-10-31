import React, { useState } from 'react';
import Navbar from './components/Navbar';
import AuctionList from './components/AuctionList';
import LiveAuctionRoom from './components/LiveAuctionRoom';
import CreateAuctionModal from './components/CreateAuctionModal';
import AuthModal from './components/AuthModal';
import ArchitectureModal from './components/ArchitectureModal';
import NotificationsModal from './components/NotificationsModal';
import { useAuth } from './context/AuthContext';
import { Globe, FileCode2, ExternalLink } from 'lucide-react';

export default function App() {
  const { isAuthenticated } = useAuth();

  const [selectedAuctionId, setSelectedAuctionId] = useState(null);
  const [createOpen, setCreateOpen] = useState(false);
  const [authOpen, setAuthOpen] = useState(false);
  const [archOpen, setArchOpen] = useState(false);
  const [notificationsOpen, setNotificationsOpen] = useState(false);

  const handleAuctionCreated = (newAuction) => {
    if (newAuction && newAuction.id) {
      setSelectedAuctionId(newAuction.id);
    }
  };

  return (
    <div style={{ display: 'flex', flexDirection: 'column', minHeight: '100vh' }}>
      {/* Top Navbar */}
      <Navbar
        onOpenCreate={() => {
          if (!isAuthenticated) {
            setAuthOpen(true);
          } else {
            setCreateOpen(true);
          }
        }}
        onOpenAuth={() => setAuthOpen(true)}
        onOpenArchitecture={() => setArchOpen(true)}
        onOpenNotifications={() => setNotificationsOpen(true)}
        onNavigateHome={() => setSelectedAuctionId(null)}
      />

      {/* Main Body */}
      <main style={{ flexGrow: 1 }}>
        {selectedAuctionId ? (
          <LiveAuctionRoom
            auctionId={selectedAuctionId}
            onBack={() => setSelectedAuctionId(null)}
            onOpenAuth={() => setAuthOpen(true)}
          />
        ) : (
          <AuctionList
            onSelectAuction={(id) => setSelectedAuctionId(id)}
            onOpenCreate={() => {
              if (!isAuthenticated) {
                setAuthOpen(true);
              } else {
                setCreateOpen(true);
              }
            }}
          />
        )}
      </main>

      {/* Site Footer */}
      <footer className="site-footer">
        <div className="container footer-inner">
          <div>
            <div style={{ fontWeight: 700, color: 'var(--text-main)', marginBottom: '0.2rem' }}>
              Distributed Auction & Bidding Sequencer Engine
            </div>
            <div>
              Engineered by{' '}
              <a href="https://jayavignesh.dev" target="_blank" rel="noreferrer" className="footer-link">
                Jayavignesh
              </a>{' '}
              • Distributed Systems Architecture
            </div>
          </div>

          <div style={{ display: 'flex', alignItems: 'center', gap: '1.25rem' }}>
            <a
              href="http://localhost:8080/swagger-ui.html"
              target="_blank"
              rel="noreferrer"
              className="footer-link"
              style={{ display: 'flex', alignItems: 'center', gap: '0.35rem' }}
            >
              <span>Swagger UI</span>
              <ExternalLink size={13} />
            </a>
            <a
              href="https://jayavignesh.dev"
              target="_blank"
              rel="noreferrer"
              className="footer-link"
              style={{ display: 'flex', alignItems: 'center', gap: '0.35rem' }}
            >
              <Globe size={14} />
              <span>jayavignesh.dev</span>
            </a>
          </div>
        </div>
      </footer>

      {/* Modals */}
      <CreateAuctionModal
        isOpen={createOpen}
        onClose={() => setCreateOpen(false)}
        onAuctionCreated={handleAuctionCreated}
        onOpenAuth={() => setAuthOpen(true)}
      />

      <AuthModal
        isOpen={authOpen}
        onClose={() => setAuthOpen(false)}
      />

      <ArchitectureModal
        isOpen={archOpen}
        onClose={() => setArchOpen(false)}
      />

      <NotificationsModal
        isOpen={notificationsOpen}
        onClose={() => setNotificationsOpen(false)}
        onSelectAuction={(id) => setSelectedAuctionId(id)}
      />
    </div>
  );
}
