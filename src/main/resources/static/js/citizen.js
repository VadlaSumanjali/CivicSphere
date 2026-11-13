(function (window) {
  'use strict';

  const API = window.CivicAPI;
  const Auth = window.CivicAuth;

  let statusChartInstance = null;

  async function initCitizenDashboard() {
    const user = await Auth.requireAuth('CITIZEN');
    if (!user) return;

    const welcomeEl = document.getElementById('welcomeBanner');
    if (welcomeEl) {
      welcomeEl.textContent = 'Welcome, ' + user.fullName;
    }

    try {
      const res = await API.request('/api/complaints/dashboard');
      const d = res.data;

      setText('kpiTotal', d.totalComplaints ?? 0);
      setText('kpiPending', d.pendingComplaints ?? 0);
      setText('kpiReview', d.underReviewComplaints ?? 0);
      setText('kpiProgress', d.inProgressComplaints ?? 0);
      setText('kpiResolved', d.resolvedComplaints ?? 0);
      setText('kpiRecurring', d.possibleRecurringIssues ?? 0);

      renderStatusChart(d.statusDistribution || {});
      renderRecentComplaints(d.recentComplaints || []);
      renderRecentActivity(d.recentActivity || []);
      renderRecurrenceAlerts(d.recurrenceAlerts || []);
    } catch (err) {
      API.showToast(err.message || 'Failed to load dashboard data.', 'error');
    }
  }

  function setText(id, val) {
    const el = document.getElementById(id);
    if (el) el.textContent = val;
  }

  function renderStatusChart(dist) {
    const canvas = document.getElementById('citizenStatusChart');
    if (!canvas || typeof Chart === 'undefined') return;

    const labels = ['SUBMITTED', 'UNDER_REVIEW', 'ASSIGNED', 'IN_PROGRESS', 'RESOLVED', 'CLOSED'];
    const values = labels.map((k) => dist[k] || 0);
    const total = values.reduce((a, b) => a + b, 0);

    const emptyMsg = document.getElementById('chartEmptyMsg');
    if (total === 0) {
      if (emptyMsg) emptyMsg.style.display = 'block';
      canvas.style.display = 'none';
      return;
    }
    if (emptyMsg) emptyMsg.style.display = 'none';
    canvas.style.display = 'block';

    if (statusChartInstance) statusChartInstance.destroy();
    statusChartInstance = new Chart(canvas, {
      type: 'doughnut',
      data: {
        labels: labels.map((l) => l.replace(/_/g, ' ')),
        datasets: [{
          data: values,
          backgroundColor: ['#D97706', '#2563EB', '#7C3AED', '#0284C7', '#16A34A', '#64748B'],
          borderWidth: 2,
          borderColor: '#FFFFFF'
        }]
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        plugins: {
          legend: { position: 'bottom', labels: { boxWidth: 12, padding: 12 } }
        }
      }
    });
  }

  function renderRecentComplaints(items) {
    const tbody = document.getElementById('recentComplaintsBody');
    if (!tbody) return;

    if (!items.length) {
      tbody.innerHTML =
        '<tr><td colspan="6" class="empty-state">No complaints submitted yet. ' +
        '<a href="' + API.url('/citizen/submit-complaint.html') + '">Report your first civic issue</a>.</td></tr>';
      return;
    }

    tbody.innerHTML = items.map((c) => {
      const recBadge = c.hasRecurrenceAlert
        ? ' <span class="badge badge-alert" title="Linked recurrence alert">Recurrence</span>'
        : '';
      return '<tr>' +
        '<td><strong>#' + c.complaintId + '</strong></td>' +
        '<td>' + API.escapeHtml(c.title) + recBadge + '</td>' +
        '<td>' + API.escapeHtml(c.categoryName) + '</td>' +
        '<td>' + API.escapeHtml(c.area + ', ' + c.city) + '</td>' +
        '<td>' + API.statusBadge(c.status) + '</td>' +
        '<td>' +
          '<a class="btn btn-outline btn-sm" href="' + API.url('/citizen/complaint-details.html?id=' + c.complaintId) + '">Details</a>' +
        '</td>' +
      '</tr>';
    }).join('');
  }

  function renderRecentActivity(items) {
    const box = document.getElementById('recentActivityList');
    if (!box) return;
    if (!items.length) {
      box.innerHTML = '<div class="empty-state">No complaint activity recorded yet.</div>';
      return;
    }
    box.innerHTML = '<div class="timeline">' + items.map((h) =>
      '<div class="timeline-item">' +
        '<div class="timeline-meta">' + API.formatDate(h.updatedAt) + ' &bull; ' +
          '<a href="' + API.url('/citizen/complaint-details.html?id=' + h.complaintId) + '">Complaint #' + h.complaintId + '</a></div>' +
        '<div class="timeline-title">' +
          (h.previousStatus ? API.escapeHtml(h.previousStatus.replace(/_/g, ' ')) + ' &rarr; ' : '') +
          API.escapeHtml(h.newStatus.replace(/_/g, ' ')) +
        '</div>' +
        (h.remarks ? '<div class="timeline-remarks">' + API.escapeHtml(h.remarks) + '</div>' : '') +
      '</div>'
    ).join('') + '</div>';
  }

  function renderRecurrenceAlerts(alerts) {
    const box = document.getElementById('recurrenceAlertsList');
    if (!box) return;

    if (!alerts.length) {
      box.innerHTML = '<div class="empty-state">No recurring issue alerts detected for your complaints.</div>';
      return;
    }

    box.innerHTML = alerts.slice(0, 5).map((r) => {
      return '<div class="recurrence-box" style="margin-bottom: 10px;">' +
        '<div style="display:flex;justify-content:space-between;align-items:center;gap:8px;margin-bottom:6px;">' +
          '<strong>Complaint #' + r.recurringComplaintId + ' &harr; #' + r.originalComplaintId + '</strong>' +
          '<span class="score-badge">Score ' + r.matchingScore + '/100</span>' +
        '</div>' +
        '<div style="font-size:0.86rem;color:var(--text-muted);margin-bottom:6px;">' + API.escapeHtml(r.matchingReason) + '</div>' +
        '<div style="display:flex;justify-content:space-between;align-items:center;">' +
          API.statusBadge(r.verificationStatus) +
          '<a style="font-size:0.84rem;font-weight:600;" href="' + API.url('/citizen/complaint-details.html?id=' + r.recurringComplaintId) + '">View Complaint &rarr;</a>' +
        '</div>' +
      '</div>';
    }).join('');
  }

  window.CivicCitizen = {
    initCitizenDashboard
  };
})(window);

