/**
 * Real-time STOMP over WebSocket Service for Distributed Auction Engine.
 * Subscribes to /topic/auctions/{auctionId} to receive live bid events.
 */

import { Client } from '@stomp/stompjs';

class WebSocketService {
  constructor() {
    this.client = null;
    this.isConnected = false;
    this.subscriptions = new Map(); // auctionId -> subscription
    this.callbacks = new Map(); // auctionId -> Set of callbacks
    this.statusListeners = new Set();
  }

  init() {
    if (this.client) return;

    // Build the WebSocket broker URL (dynamically adapts to current host/port or VITE_WS_URL)
    const isSecure = window.location.protocol === 'https:';
    const wsProto = isSecure ? 'wss://' : 'ws://';
    const host = window.location.hostname || 'localhost';
    const port = window.location.port ? `:${window.location.port}` : '';
    const wsUrl = import.meta.env.VITE_WS_URL || `${wsProto}${host}${port}/ws`;

    this.client = new Client({
      brokerURL: wsUrl,
      reconnectDelay: 3000,
      heartbeatIncoming: 4000,
      heartbeatOutgoing: 4000,
      debug: (str) => {
        // console.debug('[STOMP]', str);
      },
    });

    this.client.onConnect = (frame) => {
      console.log('[WebSocket] Connected to STOMP Broker');
      this.isConnected = true;
      this._notifyStatus('CONNECTED');

      // Re-subscribe any pending topics
      for (const [auctionId, cbs] of this.callbacks.entries()) {
        if (!this.subscriptions.has(auctionId)) {
          this._createSubscription(auctionId);
        }
      }
    };

    this.client.onDisconnect = () => {
      console.warn('[WebSocket] Disconnected from STOMP Broker');
      this.isConnected = false;
      this.subscriptions.clear();
      this._notifyStatus('DISCONNECTED');
    };

    this.client.onStompError = (frame) => {
      console.error('[WebSocket] STOMP Broker Error:', frame.headers['message'], frame.body);
      this._notifyStatus('ERROR');
    };

    this.client.onWebSocketClose = () => {
      this.isConnected = false;
      this._notifyStatus('DISCONNECTED');
    };

    this._notifyStatus('CONNECTING');
    this.client.activate();
  }

  onStatusChange(cb) {
    this.statusListeners.add(cb);
    cb(this.isConnected ? 'CONNECTED' : 'DISCONNECTED');
    return () => this.statusListeners.delete(cb);
  }

  _notifyStatus(status) {
    for (const listener of this.statusListeners) {
      try {
        listener(status);
      } catch (err) {
        console.error(err);
      }
    }
  }

  subscribeToAuction(auctionId, callback) {
    if (!this.callbacks.has(auctionId)) {
      this.callbacks.set(auctionId, new Set());
    }
    this.callbacks.get(auctionId).add(callback);

    if (this.isConnected && !this.subscriptions.has(auctionId)) {
      this._createSubscription(auctionId);
    }

    // Return un-subscriber
    return () => {
      const cbs = this.callbacks.get(auctionId);
      if (cbs) {
        cbs.delete(callback);
        if (cbs.size === 0) {
          this.callbacks.delete(auctionId);
          const sub = this.subscriptions.get(auctionId);
          if (sub) {
            sub.unsubscribe();
            this.subscriptions.delete(auctionId);
          }
        }
      }
    };
  }

  _createSubscription(auctionId) {
    if (!this.client || !this.isConnected) return;

    const topic = `/topic/auctions/${auctionId}`;
    try {
      const sub = this.client.subscribe(topic, (message) => {
        try {
          const payload = JSON.parse(message.body);
          const cbs = this.callbacks.get(auctionId);
          if (cbs) {
            cbs.forEach((cb) => cb(payload));
          }
        } catch (err) {
          console.error('[WebSocket] Failed to parse message body:', err);
        }
      });
      this.subscriptions.set(auctionId, sub);
      console.log(`[WebSocket] Subscribed to ${topic}`);
    } catch (err) {
      console.error(`[WebSocket] Subscription error for ${topic}:`, err);
    }
  }

  disconnect() {
    if (this.client) {
      this.client.deactivate();
      this.client = null;
      this.isConnected = false;
      this.subscriptions.clear();
      this.callbacks.clear();
      this._notifyStatus('DISCONNECTED');
    }
  }
}

export const wsService = new WebSocketService();

