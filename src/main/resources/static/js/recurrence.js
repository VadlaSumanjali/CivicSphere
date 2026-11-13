(function (window) {
  'use strict';

  const API = window.CivicAPI;
  const Auth = window.CivicAuth;

  let currentFilter = '';

  async function initRecurrencePage() {
    const user = await Auth.requireAuth('ADMIN');
    if (!user) return;

    document.querySelectorAll('.js-rec-filter').forEach((btn) => {
      btn.addEventListener('click', () => {
        document.querySelectorAll('.js-rec-filter').forEach((b) => {
          b.classList.remove('btn-navy');
          b.classList.add('btn-outline');
        });
        btn.classList.remove('btn-outline');
        btn.classList.add('btn-navy');
        currentFilter = btn.getAttribute('data-status') || '';
        loadRecurrences();
      });
    });

    document.querySelectorAll('[data-close-modal]').forEach((btn) => {
      btn.addEventListener('click', () => {
        document.getElementById(btn.getAttribute('data-close-modal')).classList.remove('open');
      });
    });

    const verifyForm = document.getElementById('verifyRecurrenceForm');
    if (verifyForm) {
      verifyForm.addEventListener('submit', async (e) => {
        e.preventDefault();
        const id = document.getElementById('verifyRecId').value;
        const status = document.getElementById('verifyDecision').value;
        const remarks = document.getElementById('verifyRemarks').value.trim();
        try {
          await API.request('/api/recurrence/' + id + '/verify', {
            method: 'PUT',
            body: { status, remarks }
          });
          document.getElementById('verifyRecModal').classList.remove('open');
          API.showToast('Recurrence #' + id + ' marked as ' + status, 'success');
          loadRecurrences();
        } catch (err) {
          API.showToast(err.message || 'Failed to verify recurrence.', 'error');
        }
      });
    }

    loadRecurrences();
  }

  async function loadRecurrences() {
    const listEl = document.getElementById('recurrenceList');
    listEl.innerHTML = '<div class="loading-state"><span class="spinner"></span></div>';

    try {
      const qs = currentFilter ? '?status=' + encodeURIComponent(currentFilter) : '';
      const res = await API.request('/api/recurrence' + qs);
      const items = res.data || [];
      renderRecurrenceList(items);
    } catch (err) {
      listEl.innerHTML = '<div class="error-state">' + API.escapeHtml(err.message) + '</div>';
    }
  }

  function renderRecurrenceList(items) {
    const listEl = document.getElementById('recurrenceList');
    if (!items.length) {
      listEl.innerHTML = '<div class="empty-state">No recurrence records found for this filter.</div>';
      return;
    }

    listEl.innerHTML = items.map((r) => {
      const isPending = r.verificationStatus === 'PENDING';
      const actionButtons = isPending
        ? '<button type="button" class="btn btn-success btn-sm js-verify-rec" data-id="' + r.recurrenceId + '" data-decision="CONFIRMED">Confirm Recurrence</button> ' +
          '<button type="button" class="btn btn-danger btn-sm js-verify-rec" data-id="' + r.recurrenceId + '" data-decision="REJECTED">Reject</button> '
        : '';

      return '<div class="recurrence-card">' +
        '<div style="display:flex;justify-content:space-between;align-items:center;gap:12px;flex-wrap:wrap;">' +
          '<div style="display:flex;align-items:center;gap:10px;flex-wrap:wrap;">' +
            '<strong>Recurrence Alert #' + r.recurrenceId + '</strong>' +
            '<span class="score-badge">Matching Score: ' + r.matchingScore + '/100</span>' +
            API.statusBadge(r.verificationStatus) +
            '<span style="font-size:0.84rem;color:var(--text-muted);">' + API.escapeHtml(r.categoryName) + ' &bull; ' + API.escapeHtml(r.locationSummary) + '</span>' +
          '</div>' +
          '<div>' +
            actionButtons +
            '<button type="button" class="btn btn-outline btn-sm js-view-history" data-id="' + r.originalComplaintId + '">Original History</button> ' +
            '<button type="button" class="btn btn-outline btn-sm js-view-history" data-id="' + r.recurringComplaintId + '">New History</button>' +
          '</div>' +
        '</div>' +
        '<div class="recurrence-pair">' +
          '<div class="recurrence-box">' +
            '<div style="font-size:0.78rem;text-transform:uppercase;color:var(--text-light);font-weight:700;margin-bottom:4px;">Original Historical Complaint #' + r.originalComplaintId + '</div>' +
            '<div style="font-weight:700;color:var(--navy);margin-bottom:6px;">' + API.escapeHtml(r.originalTitle) + '</div>' +
            '<div style="font-size:0.84rem;color:var(--text-muted);">' +
              'Status: ' + API.statusBadge(r.originalStatus) +
              ' &bull; Dept: ' + API.escapeHtml(r.originalDepartmentName || 'Unassigned') +
              '<br>Resolved At: ' + API.formatDate(r.originalResolvedAt) +
            '</div>' +
          '</div>' +
          '<div class="recurrence-box">' +
            '<div style="font-size:0.78rem;text-transform:uppercase;color:var(--text-light);font-weight:700;margin-bottom:4px;">New Recurring Complaint #' + r.recurringComplaintId + '</div>' +
            '<div style="font-weight:700;color:var(--navy);margin-bottom:6px;">' + API.escapeHtml(r.recurringTitle) + '</div>' +
            '<div style="font-size:0.84rem;color:var(--text-muted);">' +
              'Status: ' + API.statusBadge(r.recurringStatus) +
              ' &bull; Citizen: ' + API.escapeHtml(r.citizenName) +
              '<br>Submitted At: ' + API.formatDate(r.recurringSubmittedAt) +
            '</div>' +
          '</div>' +
        '</div>' +
        '<div style="font-size:0.88rem;color:var(--navy);background:#F8FAFC;padding:10px 12px;border-radius:var(--radius-sm);border:1px solid var(--border);">' +
          '<strong>Detection Reasoning:</strong> ' + API.escapeHtml(r.matchingReason) +
        '</div>' +
        (r.verifiedAt
          ? '<div style="margin-top:8px;font-size:0.82rem;color:var(--text-light);">Verified by ' +
              API.escapeHtml(r.verifiedByName || 'Admin') + ' on ' + API.formatDate(r.verifiedAt) +
              (r.verificationRemarks ? ' — "' + API.escapeHtml(r.verificationRemarks) + '"' : '') +
            '</div>'
          : '') +
      '</div>';
    }).join('');

    listEl.querySelectorAll('.js-verify-rec').forEach((btn) => {
      btn.addEventListener('click', () => {
        const id = btn.dataset.id;
        const decision = btn.dataset.decision;
        document.getElementById('verifyRecId').value = id;
        document.getElementById('verifyDecision').value = decision;
        document.getElementById('verifyModalTitle').textContent =
          decision === 'CONFIRMED' ? 'Confirm Recurring Issue #' + id : 'Reject Recurrence Candidate #' + id;
        document.getElementById('verifyRemarks').value = '';
        const submitBtn = document.getElementById('verifySubmitBtn');
        submitBtn.className = decision === 'CONFIRMED' ? 'btn btn-success' : 'btn btn-danger';
        submitBtn.textContent = decision === 'CONFIRMED' ? 'Confirm Recurrence' : 'Reject Candidate';
        document.getElementById('verifyRecModal').classList.add('open');
      });
    });

    listEl.querySelectorAll('.js-view-history').forEach((btn) => {
      btn.addEventListener('click', () => openComplaintHistoryModal(btn.dataset.id));
    });
  }

  async function openComplaintHistoryModal(complaintId) {
    const modal = document.getElementById('historyModal');
    const body = document.getElementById('historyModalBody');
    document.getElementById('historyModalTitle').textContent = 'Complaint #' + complaintId + ' History';
    body.innerHTML = '<div class="loading-state"><span class="spinner"></span></div>';
    modal.classList.add('open');

    try {
      const res = await API.request('/api/complaints/' + complaintId);
      const c = res.data;
      const timeline = (c.history || []).map((h) =>
        '<div class="timeline-item">' +
          '<div class="timeline-meta">' + API.formatDate(h.updatedAt) + ' &bull; ' + API.escapeHtml(h.updatedByName) + '</div>' +
          '<div class="timeline-title">' + (h.previousStatus ? API.escapeHtml(h.previousStatus) + ' &rarr; ' : '') + API.escapeHtml(h.newStatus) + '</div>' +
          (h.remarks ? '<div class="timeline-remarks">' + API.escapeHtml(h.remarks) + '</div>' : '') +
        '</div>'
      ).join('');

      body.innerHTML =
        '<p style="margin:0 0 12px;"><strong>' + API.escapeHtml(c.title) + '</strong></p>' +
        '<p style="margin:0 0 14px;font-size:0.88rem;color:var(--text-muted);">' + API.escapeHtml(c.description) + '</p>' +
        '<div class="timeline">' + (timeline || '<p>No history available.</p>') + '</div>';
    } catch (err) {
      body.innerHTML = '<div class="error-state">' + API.escapeHtml(err.message) + '</div>';
    }
  }

  window.CivicRecurrence = {
    initRecurrencePage
  };
})(window);

