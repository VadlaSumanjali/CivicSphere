(function (window) {
  'use strict';

  function getBasePath() {
    const p = window.location.pathname;
    const markers = ['/citizen/', '/admin/', '/index.html', '/login.html', '/register.html'];
    for (const m of markers) {
      const idx = p.indexOf(m);
      if (idx !== -1) {
        return p.slice(0, idx);
      }
    }
    return '';
  }

  const BASE_PATH = getBasePath();
  let csrfToken = sessionStorage.getItem('civicsphere_csrf') || '';

  function setCsrfToken(token) {
    csrfToken = token || '';
    if (csrfToken) {
      sessionStorage.setItem('civicsphere_csrf', csrfToken);
    } else {
      sessionStorage.removeItem('civicsphere_csrf');
    }
  }

  function url(path) {
    if (path.startsWith('http://') || path.startsWith('https://')) {
      return path;
    }
    return BASE_PATH + (path.startsWith('/') ? path : '/' + path);
  }

  async function request(path, options = {}) {
    const method = (options.method || 'GET').toUpperCase();
    const headers = Object.assign({}, options.headers || {});
    const isFormData = options.body instanceof FormData;

    if (!isFormData && options.body && typeof options.body === 'object' && !headers['Content-Type']) {
      headers['Content-Type'] = 'application/json';
      options.body = JSON.stringify(options.body);
    }

    if (['POST', 'PUT', 'DELETE', 'PATCH'].includes(method) && csrfToken) {
      headers['X-CSRF-Token'] = csrfToken;
    }

    const response = await fetch(url(path), {
      method,
      headers,
      body: options.body,
      credentials: 'same-origin'
    });

    let payload = null;
    const contentType = response.headers.get('content-type') || '';
    if (contentType.includes('application/json')) {
      payload = await response.json();
    } else {
      const text = await response.text();
      payload = { success: response.ok, message: text || response.statusText };
    }

    if (payload && payload.data && payload.data.csrfToken) {
      setCsrfToken(payload.data.csrfToken);
    }

    if (!response.ok || (payload && payload.success === false)) {
      const err = new Error((payload && payload.message) || 'Request failed');
      err.status = response.status;
      err.payload = payload;
      throw err;
    }

    return payload;
  }

  function escapeHtml(str) {
    if (str === null || str === undefined) return '';
    return String(str)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#39;');
  }

  function formatDate(ts) {
    if (!ts) return '—';
    const d = new Date(ts);
    if (Number.isNaN(d.getTime())) return String(ts);
    return d.toLocaleString('en-IN', {
      year: 'numeric',
      month: 'short',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit'
    });
  }

  function statusBadge(status) {
    if (!status) return '';
    const clean = String(status);
    const cls = 'badge-' + clean.toLowerCase();
    return '<span class="badge ' + escapeHtml(cls) + '">' + escapeHtml(clean.replace(/_/g, ' ')) + '</span>';
  }

  function ensureToastContainer() {
    let container = document.getElementById('toastContainer');
    if (!container) {
      container = document.createElement('div');
      container.id = 'toastContainer';
      container.className = 'toast-container';
      document.body.appendChild(container);
    }
    return container;
  }

  function showToast(message, type = 'info') {
    const container = ensureToastContainer();
    const toast = document.createElement('div');
    toast.className = 'toast toast-' + type;
    toast.innerHTML =
      '<span>' + escapeHtml(message) + '</span>' +
      '<button type="button" class="toast-close" aria-label="Close">&times;</button>';
    toast.querySelector('.toast-close').addEventListener('click', () => toast.remove());
    container.appendChild(toast);
    setTimeout(() => {
      if (toast.parentNode) toast.remove();
    }, 4500);
  }

  function showConfirmModal(title, message, confirmLabel = 'Confirm', confirmClass = 'btn-primary') {
    return new Promise((resolve) => {
      let backdrop = document.getElementById('globalConfirmModal');
      if (!backdrop) {
        backdrop = document.createElement('div');
        backdrop.id = 'globalConfirmModal';
        backdrop.className = 'modal-backdrop';
        backdrop.innerHTML =
          '<div class="modal">' +
            '<div class="modal-header">' +
              '<h3 id="gcmTitle"></h3>' +
              '<button type="button" class="toast-close" id="gcmClose">&times;</button>' +
            '</div>' +
            '<div class="modal-body" id="gcmBody"></div>' +
            '<div class="modal-footer">' +
              '<button type="button" class="btn btn-outline" id="gcmCancel">Cancel</button>' +
              '<button type="button" class="btn" id="gcmOk"></button>' +
            '</div>' +
          '</div>';
        document.body.appendChild(backdrop);
      }

      document.getElementById('gcmTitle').textContent = title;
      document.getElementById('gcmBody').textContent = message;
      const okBtn = document.getElementById('gcmOk');
      okBtn.className = 'btn ' + confirmClass;
      okBtn.textContent = confirmLabel;

      const cleanup = (result) => {
        backdrop.classList.remove('open');
        okBtn.onclick = null;
        document.getElementById('gcmCancel').onclick = null;
        document.getElementById('gcmClose').onclick = null;
        resolve(result);
      };

      okBtn.onclick = () => cleanup(true);
      document.getElementById('gcmCancel').onclick = () => cleanup(false);
      document.getElementById('gcmClose').onclick = () => cleanup(false);
      backdrop.classList.add('open');
    });
  }

  function initSidebarToggle() {
    const btn = document.getElementById('mobileMenuBtn');
    const sidebar = document.getElementById('sidebar');
    if (btn && sidebar) {
      btn.addEventListener('click', () => sidebar.classList.toggle('open'));
    }
  }

  window.CivicAPI = {
    BASE_PATH,
    url,
    request,
    setCsrfToken,
    escapeHtml,
    formatDate,
    statusBadge,
    showToast,
    showConfirmModal,
    initSidebarToggle
  };
})(window);

