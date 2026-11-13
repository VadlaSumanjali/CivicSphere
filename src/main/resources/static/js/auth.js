(function (window) {
  'use strict';

  const API = window.CivicAPI;

  async function requireAuth(expectedRole) {
    try {
      const res = await API.request('/api/auth/me');
      const user = res.data.user;
      if (!user) {
        window.location.replace(API.url('/login.html'));
        return null;
      }
      if (expectedRole && user.role !== expectedRole) {
        const target = user.role === 'ADMIN' ? '/admin/dashboard.html' : '/citizen/dashboard.html';
        window.location.replace(API.url(target));
        return null;
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
      window.location.replace(API.url('/login.html'));
      return null;
    }
  }

  async function logout() {
    try {
      await API.request('/api/auth/logout', { method: 'POST' });
    } catch (_) {
      // Ignore and redirect cleanly
    }
    API.setCsrfToken('');
    window.location.replace(API.url('/login.html'));
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

  function initLoginPage() {
    setupPasswordToggles();
    const form = document.getElementById('loginForm');
    if (!form) return;

    // Redirect if already authenticated
    API.request('/api/auth/me')
      .then((res) => {
        const user = res.data && res.data.user;
        if (user) {
          window.location.replace(API.url(user.role === 'ADMIN' ? '/admin/dashboard.html' : '/citizen/dashboard.html'));
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
      submitBtn.textContent = 'Signing in...';

      try {
        const res = await API.request('/api/auth/login', {
          method: 'POST',
          body: { email, password }
        });
        const user = res.data.user;
        API.showToast('Welcome back, ' + user.fullName, 'success');
        const dest = user.role === 'ADMIN' ? '/admin/dashboard.html' : '/citizen/dashboard.html';
        window.location.replace(API.url(dest));
      } catch (err) {
        showAuthAlert(err.message || 'Login failed. Please verify your credentials.');
        submitBtn.disabled = false;
        submitBtn.textContent = 'Sign In';
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
        API.showToast('Account created! Redirecting to dashboard...', 'success');
        window.location.replace(API.url('/citizen/dashboard.html'));
      } catch (err) {
        showAuthAlert(err.message || 'Unable to register account.');
        submitBtn.disabled = false;
        submitBtn.textContent = 'Create Citizen Account';
      }
    });
  }

  window.CivicAuth = {
    requireAuth,
    logout,
    initLoginPage,
    initRegisterPage
  };
})(window);

