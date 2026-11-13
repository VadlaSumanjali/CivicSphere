(function (window) {
  'use strict';

  const API = window.CivicAPI;

  async function requireAuth(expectedRole) {
    try {
      const res = await API.request('/api/auth/me');
      const user = res.data && res.data.user;
      if (!user) {
        redirectToLogin(expectedRole);
        return null;
      }

      // Enforce strict dashboard separation:
      // Citizens cannot access Admin dashboard; Admins cannot access Citizen dashboard as a citizen.
      if (expectedRole) {
        const expectedUpper = expectedRole.toUpperCase().replace('ROLE_', '');
        const userRoleUpper = (user.role || '').toUpperCase().replace('ROLE_', '');

        if (userRoleUpper !== expectedUpper) {
          if (userRoleUpper === 'ADMIN') {
            window.location.replace(API.url('/admin/dashboard.html'));
          } else {
            window.location.replace(API.url('/citizen/dashboard.html'));
          }
          return null;
        }
      }

      const nameEl = document.getElementById('sidebarUserName');
      const emailEl = document.getElementById('sidebarUserEmail');
      if (nameEl) nameEl.textContent = user.fullName;
      if (emailEl) emailEl.textContent = user.email;

      const logoutBtn = document.getElementById('logoutBtn');
      if (logoutBtn && !logoutBtn.dataset.bound) {
        logoutBtn.dataset.bound = '1';
        logoutBtn.addEventListener('click', logout);
      }

      API.initSidebarToggle();
      return user;
    } catch (err) {
      redirectToLogin(expectedRole);
      return null;
    }
  }

  function redirectToLogin(expectedRole) {
    const isCurrentAdmin = window.location.pathname.includes('/admin/') || expectedRole === 'ADMIN';
    const target = isCurrentAdmin ? '/admin/login.html' : '/citizen/login.html';
    window.location.replace(API.url(target));
  }

  async function logout() {
    const wasAdmin = window.location.pathname.includes('/admin/');
    try {
      await API.request('/api/auth/logout', { method: 'POST' });
    } catch (_) {
      // Ignore network errors on logout and proceed with local clearing
    }
    API.setCsrfToken('');
    const target = wasAdmin ? '/admin/login.html' : '/citizen/login.html';
    window.location.replace(API.url(target));
  }

  function setupPasswordToggles() {
    document.querySelectorAll('.password-toggle').forEach((btn) => {
      btn.addEventListener('click', () => {
        const targetId = btn.getAttribute('data-target');
        const input = document.getElementById(targetId);
        if (!input) return;
        const isPassword = input.type === 'password';
        input.type = isPassword ? 'text' : 'password';
        btn.textContent = isPassword ? 'Hide' : 'Show';
      });
    });
  }

  function showAuthAlert(msg) {
    const alertEl = document.getElementById('authAlert');
    if (alertEl) {
      alertEl.textContent = msg;
      alertEl.classList.add('show');
    }
  }

  function hideAuthAlert() {
    const alertEl = document.getElementById('authAlert');
    if (alertEl) {
      alertEl.textContent = '';
      alertEl.classList.remove('show');
    }
  }

  function initCitizenLoginPage() {
    setupPasswordToggles();
    const form = document.getElementById('loginForm');
    if (!form) return;

    // Check query params for error notifications
    const params = new URLSearchParams(window.location.search);
    if (params.get('error') === 'admin_required') {
      showAuthAlert('Access denied. Administrator privileges required to access that area.');
    }

    // Redirect if already authenticated
    API.request('/api/auth/me')
      .then((res) => {
        const user = res.data && res.data.user;
        if (user) {
          const dest = user.role === 'ADMIN' ? '/admin/dashboard.html' : '/citizen/dashboard.html';
          window.location.replace(API.url(dest));
        }
      })
      .catch(() => {});

    form.addEventListener('submit', async (e) => {
      e.preventDefault();
      hideAuthAlert();

      const email = document.getElementById('email').value.trim();
      const password = document.getElementById('password').value;
      if (!email || !password) {
        showAuthAlert('Please enter both your email address and password.');
        return;
      }

      const submitBtn = document.getElementById('loginSubmitBtn');
      submitBtn.disabled = true;
      submitBtn.textContent = 'Signing in to Citizen Dashboard...';

      try {
        const res = await API.request('/api/auth/citizen/login', {
          method: 'POST',
          body: { email, password, role: 'CITIZEN' }
        });
        const user = res.data.user;
        API.showToast('Welcome back, ' + user.fullName, 'success');
        window.location.replace(API.url('/citizen/dashboard.html'));
      } catch (err) {
        showAuthAlert(err.message || 'Citizen login failed. Please verify your credentials.');
        submitBtn.disabled = false;
        submitBtn.textContent = 'Sign In to Citizen Dashboard';
      }
    });
  }

  function initAdminLoginPage() {
    setupPasswordToggles();
    const form = document.getElementById('adminLoginForm');
    if (!form) return;

    // Check query params for notifications
    const params = new URLSearchParams(window.location.search);
    if (params.get('error') === 'admin_required') {
      showAuthAlert('Administrator privileges required. Please sign in to the Command Center.');
    } else if (params.get('error') === 'citizen_only') {
      showAuthAlert('The Citizen Portal is for citizens only. You have been returned to the Administrator Portal.');
    }

    // Redirect if already authenticated
    API.request('/api/auth/me')
      .then((res) => {
        const user = res.data && res.data.user;
        if (user) {
          const dest = user.role === 'ADMIN' ? '/admin/dashboard.html' : '/citizen/dashboard.html';
          window.location.replace(API.url(dest));
        }
      })
      .catch(() => {});

    form.addEventListener('submit', async (e) => {
      e.preventDefault();
      hideAuthAlert();

      const email = document.getElementById('email').value.trim();
      const password = document.getElementById('password').value;
      if (!email || !password) {
        showAuthAlert('Please enter your administrator email and password.');
        return;
      }

      const submitBtn = document.getElementById('adminLoginSubmitBtn');
      submitBtn.disabled = true;
      submitBtn.textContent = 'Verifying Administrative Credentials...';

      try {
        const res = await API.request('/api/auth/admin/login', {
          method: 'POST',
          body: { email, password, role: 'ADMIN' }
        });
        const user = res.data.user;
        API.showToast('Welcome, Administrator ' + user.fullName, 'success');
        window.location.replace(API.url('/admin/dashboard.html'));
      } catch (err) {
        showAuthAlert(err.message || 'Administrator authentication failed.');
        submitBtn.disabled = false;
        submitBtn.textContent = 'Sign In to Command Center';
      }
    });
  }

  function initRegisterPage() {
    setupPasswordToggles();
    const form = document.getElementById('registerForm');
    if (!form) return;

    form.addEventListener('submit', async (e) => {
      e.preventDefault();
      hideAuthAlert();

      const fullName = document.getElementById('fullName').value.trim();
      const email = document.getElementById('email').value.trim();
      const phone = document.getElementById('phone').value.trim();
      const address = document.getElementById('address').value.trim();
      const password = document.getElementById('password').value;
      const confirmPassword = document.getElementById('confirmPassword').value;

      if (fullName.length < 2) {
        showAuthAlert('Full name must be at least 2 characters.');
        return;
      }
      if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
        showAuthAlert('Please enter a valid email address.');
        return;
      }
      if (phone.replace(/\D/g, '').length < 7) {
        showAuthAlert('Please enter a valid phone number.');
        return;
      }
      if (address.length < 5) {
        showAuthAlert('Please provide your complete residential address.');
        return;
      }
      if (password.length < 8) {
        showAuthAlert('Password must be at least 8 characters long.');
        return;
      }
      if (password !== confirmPassword) {
        showAuthAlert('Password and confirm password do not match.');
        return;
      }

      const submitBtn = document.getElementById('registerSubmitBtn');
      submitBtn.disabled = true;
      submitBtn.textContent = 'Creating Account...';

      try {
        await API.request('/api/auth/register', {
          method: 'POST',
          body: { fullName, email, phone, address, password, confirmPassword }
        });
        API.showToast('Citizen account created! Redirecting to dashboard...', 'success');
        window.location.replace(API.url('/citizen/dashboard.html'));
      } catch (err) {
        showAuthAlert(err.message || 'Unable to register citizen account.');
        submitBtn.disabled = false;
        submitBtn.textContent = 'Create Citizen Account';
      }
    });
  }

  window.CivicAuth = {
    requireAuth,
    logout,
    initLoginPage: initCitizenLoginPage,
    initCitizenLoginPage,
    initAdminLoginPage,
    initRegisterPage
  };
})(window);
