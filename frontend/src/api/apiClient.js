/**
 * API client for Distributed Auction Engine REST endpoints.
 */

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || '';

class ApiClient {
  constructor() {
    this.token = localStorage.getItem('livebid_jwt') || null;
  }

  setToken(token) {
    this.token = token;
    if (token) {
      localStorage.setItem('livebid_jwt', token);
    } else {
      localStorage.removeItem('livebid_jwt');
    }
  }

  getToken() {
    return this.token;
  }

  async request(endpoint, options = {}) {
    const headers = {
      'Content-Type': 'application/json',
      ...options.headers,
    };

    if (this.token) {
      headers['Authorization'] = `Bearer ${this.token}`;
    }

    const url = `${API_BASE_URL}${endpoint}`;
    const config = {
      ...options,
      headers,
    };

    try {
      const response = await fetch(url, config);

      if (response.status === 204) {
        return null;
      }

      const contentType = response.headers.get('content-type');
      let data = null;
      if (contentType && contentType.includes('application/json')) {
        data = await response.json();
      } else {
        const text = await response.text();
        try {
          data = JSON.parse(text);
        } catch {
          data = text;
        }
      }

      if (!response.ok) {
        const errorMsg =
          (data && (data.message || data.error || data.detail)) ||
          `HTTP Error ${response.status}: ${response.statusText}`;
        const error = new Error(errorMsg);
        error.status = response.status;
        error.data = data;
        throw error;
      }

      return data;
    } catch (err) {
      console.error(`[API] Error on ${options.method || 'GET'} ${endpoint}:`, err);
      throw err;
    }
  }

  // --- Auth Endpoints ---
  async login(email, password) {
    const res = await this.request('/api/auth/login', {
      method: 'POST',
      body: JSON.stringify({ email, password }),
    });
    if (res && res.token) {
      this.setToken(res.token);
    }
    return res;
  }

  async register(name, email, password) {
    return this.request('/api/auth/register', {
      method: 'POST',
      body: JSON.stringify({ name, email, password }),
    });
  }

  // --- Auctions Endpoints ---
  async getAuctions({ status, search, minPrice, maxPrice, endingSoon, page = 0, size = 20 } = {}) {
    const params = new URLSearchParams();
    if (status) params.append('status', status);
    if (search) params.append('search', search);
    if (minPrice != null && minPrice !== '') params.append('minPrice', minPrice);
    if (maxPrice != null && maxPrice !== '') params.append('maxPrice', maxPrice);
    if (endingSoon) params.append('endingSoon', 'true');
    params.append('page', page);
    params.append('size', size);

    const query = params.toString() ? `?${params.toString()}` : '';
    return this.request(`/api/auctions${query}`);
  }

  async getAuctionById(id) {
    return this.request(`/api/auctions/${id}`);
  }

  async createAuction(auctionData) {
    return this.request('/api/auctions', {
      method: 'POST',
      body: JSON.stringify(auctionData),
    });
  }

  // --- Bidding Endpoints ---
  async placeBid(auctionId, amount, autoBid = false) {
    return this.request(`/api/auctions/${auctionId}/bids`, {
      method: 'POST',
      body: JSON.stringify({ amount: parseFloat(amount), autoBid }),
    });
  }

  async getBidHistory(auctionId) {
    return this.request(`/api/auctions/${auctionId}/bids`);
  }

  // --- Auto-Bid Bot Endpoints ---
  async setAutoBid(auctionId, maxAmount) {
    return this.request(`/api/auctions/${auctionId}/auto-bid`, {
      method: 'POST',
      body: JSON.stringify({ maxAmount: parseFloat(maxAmount) }),
    });
  }

  async deactivateAutoBid(auctionId) {
    return this.request(`/api/auctions/${auctionId}/auto-bid`, {
      method: 'DELETE',
    });
  }

  // --- Notifications ---
  async getNotifications() {
    return this.request('/api/users/me/notifications');
  }
}

export const api = new ApiClient();

