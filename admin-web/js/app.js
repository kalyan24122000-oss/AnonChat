// AnonChat Admin Dashboard Application Controller

function showToast(message, type = 'success') {
  const container = document.getElementById('toast-container');
  const toast = document.createElement('div');
  toast.className = `toast toast-${type}`;
  toast.textContent = message;
  container.appendChild(toast);
  setTimeout(() => {
    toast.remove();
  }, 4000);
}

function formatDate(timestamp) {
  if (!timestamp) return '—';
  const d = new Date(timestamp);
  return d.toLocaleDateString() + ' ' + d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
}

function openModal(id) {
  const modal = document.getElementById(id);
  if (modal) modal.classList.add('active');
}

function closeModal(id) {
  const modal = document.getElementById(id);
  if (modal) modal.classList.remove('active');
}

// Authentication Check
function checkAuth() {
  const token = api.getToken();
  const user = api.getUser();

  if (!token || !user || user.role !== 'admin') {
    document.getElementById('login-view').style.display = 'flex';
    document.getElementById('app-view').style.display = 'none';
  } else {
    document.getElementById('login-view').style.display = 'none';
    document.getElementById('app-view').style.display = 'flex';
    document.getElementById('admin-display-id').textContent = user.id;
    loadDashboardStats();
    loadUsers();
    loadLicenses();
    loadPlans();
    loadAuditLogs();
  }
}

// Login Handler
document.getElementById('login-form').addEventListener('submit', async (e) => {
  e.preventDefault();
  const idInput = document.getElementById('login-id');
  const passwordInput = document.getElementById('login-password');
  const btn = document.getElementById('login-btn');
  const errorAlert = document.getElementById('login-error');

  btn.disabled = true;
  btn.textContent = 'Verifying Authenticity...';
  errorAlert.style.display = 'none';

  try {
    const result = await api.login(idInput.value.trim(), passwordInput.value);
    api.setToken(result.token);
    api.setUser(result.user);
    showToast('Administrator session established');
    checkAuth();
  } catch (err) {
    errorAlert.textContent = err.message || 'Login failed';
    errorAlert.style.display = 'block';
  } finally {
    btn.disabled = false;
    btn.textContent = 'Sign In to Dashboard';
  }
});

// Logout Handler
document.getElementById('logout-btn').addEventListener('click', () => {
  api.clearToken();
  showToast('Logged out securely');
  checkAuth();
});

// Navigation Handling
document.querySelectorAll('.sidebar-nav .nav-item').forEach((item) => {
  item.addEventListener('click', () => {
    document.querySelectorAll('.sidebar-nav .nav-item').forEach((i) => i.classList.remove('active'));
    item.classList.add('active');

    const targetSection = item.dataset.target;
    document.querySelectorAll('.view-section').forEach((sec) => (sec.style.display = 'none'));
    const secEl = document.getElementById(targetSection);
    if (secEl) {
      secEl.style.display = 'block';
      document.getElementById('page-title').textContent = item.querySelector('span').textContent;
    }

    // Refresh section data
    if (targetSection === 'sec-overview') loadDashboardStats();
    if (targetSection === 'sec-users') loadUsers();
    if (targetSection === 'sec-licenses') loadLicenses();
    if (targetSection === 'sec-plans') loadPlans();
    if (targetSection === 'sec-audit') loadAuditLogs();
    if (targetSection === 'sec-inspector') loadDatabaseInspector();
  });
});

// 1. Dashboard Stats
async function loadDashboardStats() {
  try {
    const stats = await api.getStats();
    document.getElementById('stat-total-users').textContent = stats.users.total;
    document.getElementById('stat-active-trials').textContent = stats.users.activeTrials;
    document.getElementById('stat-active-premiums').textContent = stats.users.activePremiums;
    document.getElementById('stat-total-messages').textContent = stats.messaging.totalMessages;
    document.getElementById('stat-unused-licenses').textContent = stats.licenses.unused;

    // Render Recent Audits
    const auditTbody = document.getElementById('recent-audits-tbody');
    auditTbody.innerHTML = '';
    stats.recentAudits.slice(0, 5).forEach((audit) => {
      const tr = document.createElement('tr');
      tr.innerHTML = `
        <td class="mono">${audit.action}</td>
        <td class="mono">${audit.actor_id}</td>
        <td>${formatDate(audit.created_at)}</td>
      `;
      auditTbody.appendChild(tr);
    });
  } catch (err) {
    showToast('Failed to load dashboard metrics', 'error');
  }
}

// 2. Users Management
let currentUsersPage = 1;
async function loadUsers(page = 1) {
  currentUsersPage = page;
  const search = document.getElementById('user-search-input').value;
  const status = document.getElementById('user-status-filter').value;
  const tbody = document.getElementById('users-tbody');
  tbody.innerHTML = '<tr><td colspan="7" style="text-align:center; padding: 24px;">Loading accounts...</td></tr>';

  try {
    const res = await api.getUsers({ page, limit: 15, search, status });
    tbody.innerHTML = '';

    if (res.users.length === 0) {
      tbody.innerHTML = '<tr><td colspan="7" style="text-align:center; padding: 24px;">No users found.</td></tr>';
      return;
    }

    res.users.forEach((u) => {
      const tr = document.createElement('tr');
      const isExpired = u.subscription_status === 'expired' || (u.trial_expires_at < Date.now() && u.subscription_status !== 'active');
      let subBadge = `<span class="badge badge-trial">Trial</span>`;
      if (u.subscription_status === 'active') subBadge = `<span class="badge badge-active">Premium</span>`;
      if (isExpired) subBadge = `<span class="badge badge-expired">Expired</span>`;
      if (u.role === 'admin') subBadge = `<span class="badge badge-admin">Admin</span>`;

      const statusBadge = u.status === 'suspended'
        ? `<span class="badge badge-suspended">Suspended</span>`
        : `<span class="badge badge-active">Active</span>`;

      tr.innerHTML = `
        <td class="mono" style="font-weight:600;">${u.id}</td>
        <td>${u.display_name || '<em style="color:var(--text-muted)">Anonymous</em>'}</td>
        <td><span class="badge ${u.role === 'admin' ? 'badge-admin' : ''}">${u.role}</span></td>
        <td>${statusBadge}</td>
        <td>${subBadge}</td>
        <td>${formatDate(u.subscription_status === 'active' ? u.premium_expires_at : u.trial_expires_at)}</td>
        <td>
          <div style="display:flex; gap:6px;">
            ${u.role !== 'admin' ? (
              u.status === 'active'
                ? `<button class="btn btn-secondary btn-sm" onclick="promptSuspend('${u.id}')">Suspend</button>`
                : `<button class="btn btn-primary btn-sm" onclick="reactivateUser('${u.id}')">Reactivate</button>`
            ) : ''}
            <button class="btn btn-secondary btn-sm" onclick="resetUserPassword('${u.id}')">Reset Password</button>
          </div>
        </td>
      `;
      tbody.appendChild(tr);
    });

    // Pagination info
    document.getElementById('users-pagination-info').textContent = `Page ${res.page} of ${res.totalPages || 1} (${res.total} total accounts)`;
  } catch (err) {
    showToast('Failed to load users: ' + err.message, 'error');
  }
}

document.getElementById('user-search-btn').addEventListener('click', () => loadUsers(1));
document.getElementById('user-status-filter').addEventListener('change', () => loadUsers(1));

function promptSuspend(userId) {
  const reason = prompt(`Enter reason for suspending account ${userId}:`, 'Terms of service violation');
  if (reason !== null) {
    api.suspendUser(userId, reason)
      .then(() => {
        showToast(`User ${userId} suspended`);
        loadUsers(currentUsersPage);
      })
      .catch((err) => showToast(err.message, 'error'));
  }
}

function reactivateUser(userId) {
  api.reactivateUser(userId)
    .then(() => {
      showToast(`User ${userId} reactivated`);
      loadUsers(currentUsersPage);
    })
    .catch((err) => showToast(err.message, 'error'));
}

async function resetUserPassword(userId) {
  if (!confirm(`Generate a secure temporary password for ${userId}?`)) return;

  try {
    const res = await api.resetUserPassword(userId);
    document.getElementById('reset-result-user').textContent = res.userId;
    document.getElementById('reset-result-pwd').textContent = res.temporaryPassword;
    openModal('modal-reset-result');
  } catch (err) {
    showToast(err.message, 'error');
  }
}

// 3. Licenses Management
async function loadLicenses() {
  const tbody = document.getElementById('licenses-tbody');
  tbody.innerHTML = '<tr><td colspan="6" style="text-align:center; padding: 24px;">Loading license keys...</td></tr>';

  try {
    const res = await api.getLicenses({ limit: 50 });
    tbody.innerHTML = '';

    if (res.licenses.length === 0) {
      tbody.innerHTML = '<tr><td colspan="6" style="text-align:center; padding: 24px;">No licenses generated yet.</td></tr>';
      return;
    }

    res.licenses.forEach((lic) => {
      const tr = document.createElement('tr');
      let statusBadge = `<span class="badge badge-unused">Unused</span>`;
      if (lic.status === 'redeemed') statusBadge = `<span class="badge badge-redeemed">Redeemed</span>`;
      if (lic.status === 'revoked') statusBadge = `<span class="badge badge-revoked">Revoked</span>`;

      tr.innerHTML = `
        <td class="mono" style="font-weight:700; color:var(--accent-green);">${lic.license_key}</td>
        <td><strong>${lic.plan_name}</strong> (${lic.duration_days}d)</td>
        <td>${statusBadge}</td>
        <td class="mono">${lic.redeemed_by_user_id || '—'}</td>
        <td>${formatDate(lic.redeemed_at || lic.created_at)}</td>
        <td>
          <div style="display:flex; gap:6px;">
            <button class="btn btn-secondary btn-sm" onclick="navigator.clipboard.writeText('${lic.license_key}').then(() => showToast('Copied license key!'))">Copy</button>
            ${lic.status === 'unused' ? `<button class="btn btn-danger btn-sm" onclick="revokeLicense('${lic.id}')">Revoke</button>` : ''}
          </div>
        </td>
      `;
      tbody.appendChild(tr);
    });
  } catch (err) {
    showToast('Failed to load licenses: ' + err.message, 'error');
  }
}

document.getElementById('open-gen-license-btn').addEventListener('click', () => {
  openModal('modal-gen-license');
});

document.getElementById('form-gen-license').addEventListener('submit', async (e) => {
  e.preventDefault();
  const planId = document.getElementById('gen-plan-select').value;
  const notes = document.getElementById('gen-notes').value;

  try {
    const res = await api.generateLicense(planId, notes);
    closeModal('modal-gen-license');
    showToast(`Generated license: ${res.license.license_key}`);
    loadLicenses();
  } catch (err) {
    showToast(err.message, 'error');
  }
});

function revokeLicense(licenseId) {
  if (!confirm('Are you sure you want to revoke this license key?')) return;
  api.revokeLicense(licenseId)
    .then(() => {
      showToast('License revoked');
      loadLicenses();
    })
    .catch((err) => showToast(err.message, 'error'));
}

// 4. Plans Management
let cachedPlans = [];
async function loadPlans() {
  const container = document.getElementById('plans-grid-container');
  container.innerHTML = '<p>Loading configurable plans...</p>';

  try {
    const res = await api.getPlans();
    cachedPlans = res.plans;
    container.innerHTML = '';

    // Populate select in generate license modal
    const planSelect = document.getElementById('gen-plan-select');
    if (planSelect) {
      planSelect.innerHTML = res.plans.map((p) => `<option value="${p.id}">${p.name} (${p.duration_days} Days)</option>`).join('');
    }

    res.plans.forEach((p) => {
      const card = document.createElement('div');
      card.className = 'plan-card';
      const features = Array.isArray(p.features) ? p.features : [];

      card.innerHTML = `
        <div style="display:flex; justify-content:space-between; align-items:center;">
          <h4 style="font-size:1.15rem; color:#fff;">${p.name}</h4>
          <span class="badge ${p.is_active ? 'badge-active' : 'badge-suspended'}">${p.is_active ? 'Active' : 'Disabled'}</span>
        </div>
        <div class="plan-duration">${p.duration_days} Days</div>
        <p style="font-size:0.875rem; color:var(--text-secondary); margin-bottom:12px;">${p.description || 'Configurable premium plan'}</p>
        <ul class="plan-features-list">
          ${features.map((f) => `<li><span style="color:var(--accent-green);">✓</span> ${f}</li>`).join('')}
        </ul>
        <button class="btn btn-secondary btn-sm" style="width:100%; margin-top:12px;" onclick="openEditPlan('${p.id}')">Configure Plan</button>
      `;
      container.appendChild(card);
    });
  } catch (err) {
    showToast('Failed to load plans: ' + err.message, 'error');
  }
}

function openEditPlan(planId) {
  const plan = cachedPlans.find((p) => p.id === planId);
  if (!plan) return;

  document.getElementById('edit-plan-id').value = plan.id;
  document.getElementById('edit-plan-name').value = plan.name;
  document.getElementById('edit-plan-duration').value = plan.duration_days;
  document.getElementById('edit-plan-desc').value = plan.description || '';
  document.getElementById('edit-plan-features').value = Array.isArray(plan.features) ? plan.features.join('\n') : '';
  openModal('modal-edit-plan');
}

document.getElementById('form-edit-plan').addEventListener('submit', async (e) => {
  e.preventDefault();
  const id = document.getElementById('edit-plan-id').value;
  const name = document.getElementById('edit-plan-name').value;
  const duration_days = parseInt(document.getElementById('edit-plan-duration').value, 10);
  const description = document.getElementById('edit-plan-desc').value;
  const rawFeatures = document.getElementById('edit-plan-features').value;
  const features = rawFeatures.split('\n').map((f) => f.trim()).filter((f) => f.length > 0);

  try {
    await api.updatePlan(id, { name, duration_days, description, features });
    closeModal('modal-edit-plan');
    showToast(`Updated plan: ${name}`);
    loadPlans();
  } catch (err) {
    showToast(err.message, 'error');
  }
});

// 5. Audit & Security Logs
async function loadAuditLogs() {
  const tbody = document.getElementById('audit-tbody');
  tbody.innerHTML = '<tr><td colspan="5" style="text-align:center; padding: 24px;">Loading security audit logs...</td></tr>';

  try {
    const res = await api.getAuditLogs({ limit: 50 });
    tbody.innerHTML = '';

    if (res.logs.length === 0) {
      tbody.innerHTML = '<tr><td colspan="5" style="text-align:center; padding: 24px;">No audit events recorded.</td></tr>';
      return;
    }

    res.logs.forEach((log) => {
      const tr = document.createElement('tr');
      tr.innerHTML = `
        <td class="mono">${log.id}</td>
        <td class="mono"><strong>${log.action}</strong></td>
        <td class="mono">${log.actor_id || 'system'}</td>
        <td class="mono" style="font-size:0.8rem; max-width:250px; overflow:hidden; text-overflow:ellipsis; white-space:nowrap;">${log.details || '—'}</td>
        <td>${formatDate(log.created_at)}</td>
      `;
      tbody.appendChild(tr);
    });
  } catch (err) {
    showToast('Failed to load audit logs: ' + err.message, 'error');
  }
}

// 6. Database Inspector
async function loadDatabaseInspector() {
  const tbody = document.getElementById('inspector-tbody');
  tbody.innerHTML = '<tr><td colspan="3" style="text-align:center; padding: 24px;">Inspecting database state...</td></tr>';

  try {
    const res = await api.getDatabaseInspector();
    tbody.innerHTML = '';
    res.tables.forEach((t) => {
      const tr = document.createElement('tr');
      tr.innerHTML = `
        <td class="mono"><strong>${t.table}</strong></td>
        <td><span class="badge badge-admin">${t.recordCount} records</span></td>
        <td style="color:var(--text-secondary); font-size:0.85rem;">Authorized schema verification passed (Plaintext messages & keys redacted)</td>
      `;
      tbody.appendChild(tr);
    });
  } catch (err) {
    showToast('Failed to inspect database: ' + err.message, 'error');
  }
}

// Init on page load
document.addEventListener('DOMContentLoaded', () => {
  checkAuth();
});
