// AnonChat Admin Panel API Client

const API_BASE = window.location.origin.includes('localhost') || window.location.origin.includes('127.0.0.1')
  ? `${window.location.origin}/api`
  : '/api';

const api = {
  getToken() {
    return localStorage.getItem('anonchat_admin_token');
  },
  
  setToken(token) {
    localStorage.setItem('anonchat_admin_token', token);
  },

  clearToken() {
    localStorage.removeItem('anonchat_admin_token');
    localStorage.removeItem('anonchat_admin_user');
  },

  getUser() {
    try {
      return JSON.parse(localStorage.getItem('anonchat_admin_user'));
    } catch (e) {
      return null;
    }
  },

  setUser(user) {
    localStorage.setItem('anonchat_admin_user', JSON.stringify(user));
  },

  async request(endpoint, options = {}) {
    const token = this.getToken();
    const headers = {
      'Content-Type': 'application/json',
      ...(options.headers || {})
    };

    if (token) {
      headers['Authorization'] = `Bearer ${token}`;
    }

    const config = {
      ...options,
      headers
    };

    if (config.body && typeof config.body === 'object') {
      config.body = JSON.stringify(config.body);
    }

    const response = await fetch(`${API_BASE}${endpoint}`, config);
    const data = await response.json().catch(() => ({}));

    if (!response.ok) {
      if (response.status === 401 || (response.status === 403 && data.error === 'FORBIDDEN')) {
        // If token expired or invalid role
        if (!endpoint.includes('/login')) {
          this.clearToken();
          window.location.reload();
        }
      }
      const err = new Error(data.message || 'API request failed');
      err.code = data.error;
      err.status = response.status;
      throw err;
    }

    return data;
  },

  // Admin Auth
  login(id, password) {
    return this.request('/admin/login', {
      method: 'POST',
      body: { id, password }
    });
  },

  // Dashboard Stats
  getStats() {
    return this.request('/admin/stats');
  },

  // Users
  getUsers(params = {}) {
    const query = new URLSearchParams(params).toString();
    return this.request(`/admin/users?${query}`);
  },

  suspendUser(userId, reason) {
    return this.request(`/admin/users/${userId}/suspend`, {
      method: 'POST',
      body: { reason }
    });
  },

  reactivateUser(userId) {
    return this.request(`/admin/users/${userId}/reactivate`, {
      method: 'POST'
    });
  },

  resetUserPassword(userId) {
    return this.request(`/admin/users/${userId}/reset-password`, {
      method: 'POST'
    });
  },

  // Licenses
  getLicenses(params = {}) {
    const query = new URLSearchParams(params).toString();
    return this.request(`/admin/licenses?${query}`);
  },

  generateLicense(planId, notes) {
    return this.request('/admin/licenses/generate', {
      method: 'POST',
      body: { planId, notes }
    });
  },

  revokeLicense(licenseId) {
    return this.request(`/admin/licenses/${licenseId}/revoke`, {
      method: 'POST'
    });
  },

  // Plans
  getPlans() {
    return this.request('/admin/plans');
  },

  updatePlan(planId, data) {
    return this.request(`/admin/plans/${planId}`, {
      method: 'PUT',
      body: data
    });
  },

  // Audit Logs
  getAuditLogs(params = {}) {
    const query = new URLSearchParams(params).toString();
    return this.request(`/admin/audit-logs?${query}`);
  },

  // Database Inspector
  getDatabaseInspector() {
    return this.request('/admin/database-inspector');
  }
};
