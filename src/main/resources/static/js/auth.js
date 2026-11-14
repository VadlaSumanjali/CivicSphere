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

  function initUnifiedLoginPage() {
    setupPasswordToggles();
    const form = document.getElementById('loginForm');
    if (!form) return;

    let currentRole = 'CITIZEN';
    const tabCitizen = document.getElementById('tabCitizen');
    const tabAdmin = document.getElementById('tabAdmin');
    const badgeCitizen = document.getElementById('badgeCitizen');
    const badgeAdmin = document.getElementById('badgeAdmin');
    const pageTitle = document.getElementById('pageTitle');
    const pageSub = document.getElementById('pageSub');
    const adminBanner = document.getElementById('adminBanner');
    const submitBtn = document.getElementById('loginSubmitBtn');
    const citizenFooter = document.getElementById('citizenFooter');
    const emailLabel = document.getElementById('emailLabel');
    const emailInput = document.getElementById('email');

    function switchRole(role) {
      currentRole = role;
      hideAuthAlert();
      if (role === 'ADMIN') {
        if (tabCitizen) {
          tabCitizen.classList.remove('active');
          tabCitizen.setAttribute('aria-selected', 'false');
        }
        if (tabAdmin) {
          tabAdmin.classList.add('active');
          tabAdmin.setAttribute('aria-selected', 'true');
        }
        if (badgeCitizen) badgeCitizen.style.display = 'none';
        if (badgeAdmin) badgeAdmin.style.display = 'inline-flex';
        if (adminBanner) adminBanner.style.display = 'flex';
        if (citizenFooter) citizenFooter.style.display = 'none';
        if (pageTitle) pageTitle.textContent = 'Administrator Sign In';
        if (pageSub) pageSub.textContent = 'Municipal Command Center triage terminal.';
        if (emailLabel) emailLabel.textContent = 'Administrator Email Address';
        if (emailInput) emailInput.placeholder = 'admin@civicsphere.gov.local';
        if (submitBtn) {
          submitBtn.textContent = 'Sign In to Command Center';
          submitBtn.style.background = '#0F172A';
          submitBtn.style.borderColor = '#0F172A';
        }
      } else {
        if (tabCitizen) {
          tabCitizen.classList.add('active');
          tabCitizen.setAttribute('aria-selected', 'true');
        }
        if (tabAdmin) {
          tabAdmin.classList.remove('active');
          tabAdmin.setAttribute('aria-selected', 'false');
        }
        if (badgeCitizen) badgeCitizen.style.display = 'inline-flex';
        if (badgeAdmin) badgeAdmin.style.display = 'none';
        if (adminBanner) adminBanner.style.display = 'none';
        if (citizenFooter) citizenFooter.style.display = 'block';
        if (pageTitle) pageTitle.textContent = 'Citizen Sign In';
        if (pageSub) pageSub.textContent = 'Access your personal complaint dashboard and active reports.';
        if (emailLabel) emailLabel.textContent = 'Citizen Email Address';
        if (emailInput) emailInput.placeholder = 'citizen@example.com';
        if (submitBtn) {
          submitBtn.textContent = 'Sign In to Citizen Dashboard';
          submitBtn.style.background = '';
          submitBtn.style.borderColor = '';
        }
      }
    }

    if (tabCitizen) tabCitizen.addEventListener('click', () => switchRole('CITIZEN'));
    if (tabAdmin) tabAdmin.addEventListener('click', () => switchRole('ADMIN'));

    // Check query params for alerts/redirects
    const params = new URLSearchParams(window.location.search);
    if (params.get('portal') === 'admin' || params.get('error') === 'admin_required') {
      switchRole('ADMIN');
      if (params.get('error') === 'admin_required') {
        showAuthAlert('Administrator privileges required. Please sign in to the Command Center.');
      }
    } else if (params.get('error') === 'citizen_only') {
      switchRole('CITIZEN');
      showAuthAlert('The Citizen Portal is for citizens only.');
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

      const email = emailInput.value.trim();
      const password = document.getElementById('password').value;
      if (!email || !password) {
        showAuthAlert('Please enter both your email address and password.');
        return;
      }

      submitBtn.disabled = true;
      submitBtn.textContent = currentRole === 'ADMIN' ? 'Verifying Administrator Credentials...' : 'Signing in to Citizen Dashboard...';

      const endpoint = currentRole === 'ADMIN' ? '/api/auth/admin/login' : '/api/auth/citizen/login';

      try {
        const res = await API.request(endpoint, {
          method: 'POST',
          body: { email, password, role: currentRole }
        });
        const user = res.data.user;
        API.showToast((currentRole === 'ADMIN' ? 'Welcome, Administrator ' : 'Welcome back, ') + user.fullName, 'success');
        const dest = (user.role === 'ADMIN' || currentRole === 'ADMIN') ? '/admin/dashboard.html' : '/citizen/dashboard.html';
        window.location.replace(API.url(dest));
      } catch (err) {
        showAuthAlert(err.message || 'Authentication failed. Please verify your credentials.');
        submitBtn.disabled = false;
        submitBtn.textContent = currentRole === 'ADMIN' ? 'Sign In to Command Center' : 'Sign In to Citizen Dashboard';
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
    initLoginPage: initUnifiedLoginPage,
    initUnifiedLoginPage,
    initCitizenLoginPage,
    initAdminLoginPage,
    initRegisterPage
  };
})(window);
