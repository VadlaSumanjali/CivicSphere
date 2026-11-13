(function (window) {
  'use strict';

  const API = window.CivicAPI;
  const Auth = window.CivicAuth;

  const charts = {};
  let currentPage = 1;
  const pageSize = 12;
  let activeDepartments = [];
  let currentFilterState = {};

  function destroyChart(key) {
    if (charts[key]) {
      charts[key].destroy();
      charts[key] = null;
    }
  }

  async function initAdminDashboard() {
    const user = await Auth.requireAuth('ADMIN');
    if (!user) return;

    try {
      const res = await API.request('/api/admin/dashboard');
      const d = res.data;

      setText('kpiTotal', d.totalComplaints ?? 0);
      setText('kpiPending', d.pendingComplaints ?? 0);
      setText('kpiInProgress', d.inProgressComplaints ?? 0);
      setText('kpiResolved', d.resolvedComplaints ?? 0);
      setText('kpiCandidates', d.totalRecurrenceCandidates ?? 0);
      setText('kpiConfirmedRec', d.confirmedRecurringIssues ?? 0);
      setText('kpiOpenRec', d.openRecurringIssues ?? 0);

      renderAdminCharts(d);
      renderRecentActivity(d.recentComplaints || []);
      renderPendingRecurrenceAlerts(d.pendingRecurrenceAlerts || []);
    } catch (err) {
      API.showToast(err.message || 'Failed to load admin analytics.', 'error');
    }
  }

  function setText(id, val) {
    const el = document.getElementById(id);
    if (el) el.textContent = val;
  }

  function renderAdminCharts(d) {
    if (typeof Chart === 'undefined') return;

    const palette = ['#142B45', '#0D9488', '#2563EB', '#D97706', '#7C3AED', '#16A34A', '#DC2626'];

    // 1. Complaints by Category
    const catData = d.byCategory || [];
    destroyChart('cat');
    const catCtx = document.getElementById('chartByCategory');
    if (catCtx) {
      charts.cat = new Chart(catCtx, {
        type: 'bar',
        data: {
          labels: catData.map((r) => r.label),
          datasets: [{
            label: 'Complaints',
            data: catData.map((r) => r.count),
            backgroundColor: '#0D9488',
            borderRadius: 6
          }]
        },
        options: {
          responsive: true,
          maintainAspectRatio: false,
          plugins: { legend: { display: false } },
          scales: { y: { beginAtZero: true, ticks: { precision: 0 } } }
        }
      });
    }

    // 2. Complaints by Status
    const statusData = d.byStatus || [];
    destroyChart('status');
    const statusCtx = document.getElementById('chartByStatus');
    if (statusCtx) {
      charts.status = new Chart(statusCtx, {
        type: 'doughnut',
        data: {
          labels: statusData.map((r) => String(r.label).replace(/_/g, ' ')),
          datasets: [{
            data: statusData.map((r) => r.count),
            backgroundColor: palette,
            borderWidth: 2,
            borderColor: '#FFFFFF'
          }]
        },
        options: {
          responsive: true,
          maintainAspectRatio: false,
          plugins: { legend: { position: 'bottom', labels: { boxWidth: 12 } } }
        }
      });
    }

    // 3. Complaints by Department
    const deptData = d.byDepartment || [];
    destroyChart('dept');
    const deptCtx = document.getElementById('chartByDepartment');
    if (deptCtx) {
      charts.dept = new Chart(deptCtx, {
        type: 'bar',
        data: {
          labels: deptData.map((r) => r.label),
          datasets: [{
            label: 'Assigned',
            data: deptData.map((r) => r.count),
            backgroundColor: '#142B45',
            borderRadius: 6
          }]
        },
        options: {
          indexAxis: 'y',
          responsive: true,
          maintainAspectRatio: false,
          plugins: { legend: { display: false } },
          scales: { x: { beginAtZero: true, ticks: { precision: 0 } } }
        }
      });
    }

    // 4. Monthly Complaint Trends
    const monthlyData = d.monthlyComplaints || [];
    destroyChart('monthly');
    const monthlyCtx = document.getElementById('chartMonthlyTrends');
    if (monthlyCtx) {
      charts.monthly = new Chart(monthlyCtx, {
        type: 'line',
        data: {
          labels: monthlyData.map((r) => r.label),
          datasets: [{
            label: 'Monthly Complaints',
            data: monthlyData.map((r) => r.count),
            borderColor: '#0D9488',
            backgroundColor: 'rgba(13, 148, 136, 0.14)',
            fill: true,
            tension: 0.3
          }]
        },
        options: {
          responsive: true,
          maintainAspectRatio: false,
          scales: { y: { beginAtZero: true, ticks: { precision: 0 } } }
        }
      });
    }

    // 5. Recurring Issue Trends
    const recTrends = d.monthlyRecurrences || [];
    destroyChart('recTrends');
    const recCtx = document.getElementById('chartRecurrenceTrends');
    if (recCtx) {
      charts.recTrends = new Chart(recCtx, {
        type: 'bar',
        data: {
          labels: recTrends.map((r) => r.label),
          datasets: [
            {
              label: 'Total Candidates',
              data: recTrends.map((r) => r.total),
              backgroundColor: '#D97706',
              borderRadius: 5
            },
            {
              label: 'Confirmed Recurrences',
              data: recTrends.map((r) => r.confirmed),
              backgroundColor: '#DC2626',
              borderRadius: 5
            }
          ]
        },
        options: {
          responsive: true,
          maintainAspectRatio: false,
          scales: { y: { beginAtZero: true, ticks: { precision: 0 } } }
        }
      });
    }
  }

  function renderRecentActivity(items) {
    const tbody = document.getElementById('adminRecentBody');
    if (!tbody) return;
    if (!items.length) {
      tbody.innerHTML = '<tr><td colspan="5" class="empty-state">No complaints recorded yet.</td></tr>';
      return;
    }
    tbody.innerHTML = items.map((c) =>
      '<tr>' +
        '<td><strong>#' + c.complaintId + '</strong></td>' +
        '<td>' + API.escapeHtml(c.title) + '</td>' +
        '<td>' + API.escapeHtml(c.citizenName) + '</td>' +
        '<td>' + API.statusBadge(c.status) + '</td>' +
        '<td>' + API.formatDate(c.submittedAt) + '</td>' +
      '</tr>'
    ).join('');
  }

  function renderPendingRecurrenceAlerts(alerts) {
    const container = document.getElementById('adminPendingRecurrences');
    if (!container) return;
    if (!alerts.length) {
      container.innerHTML = '<div class="empty-state">No pending recurrence alerts awaiting review.</div>';
      return;
    }
    container.innerHTML = alerts.map((r) =>
      '<div class="recurrence-box" style="margin-bottom:10px;">' +
        '<div style="display:flex;justify-content:space-between;align-items:center;gap:8px;margin-bottom:6px;">' +
          '<strong>#' + r.recurringComplaintId + ' &harr; Original #' + r.originalComplaintId + '</strong>' +
          '<span class="score-badge">' + r.matchingScore + '/100</span>' +
        '</div>' +
        '<div style="font-size:0.86rem;color:var(--text-muted);margin-bottom:6px;">' + API.escapeHtml(r.locationSummary) + ' &bull; ' + API.escapeHtml(r.categoryName) + '</div>' +
        '<a style="font-size:0.84rem;font-weight:600;" href="' + API.url('/admin/recurrence.html') + '">Review in Recurrence Queue &rarr;</a>' +
      '</div>'
    ).join('');
  }

  // ===================== ADMIN COMPLAINTS PAGE =====================
  async function initAdminComplaintsPage() {
    const user = await Auth.requireAuth('ADMIN');
    if (!user) return;

    await Promise.all([loadCategories(), loadActiveDepartments()]);

    const searchInput = document.getElementById('adminSearch');
    const catSelect = document.getElementById('adminFilterCategory');
    const statusSelect = document.getElementById('adminFilterStatus');
    const locInput = document.getElementById('adminFilterLocation');
    const applyBtn = document.getElementById('adminApplyFilters');
    const resetBtn = document.getElementById('adminResetFilters');

    const load = async (page = 1) => {
      currentPage = page;
      currentFilterState = {
        search: searchInput.value.trim(),
        categoryId: catSelect.value,
        status: statusSelect.value,
        location: locInput.value.trim()
      };
      const qs = new URLSearchParams({
        page: String(currentPage),
        pageSize: String(pageSize)
      });
      if (currentFilterState.search) qs.set('search', currentFilterState.search);
      if (currentFilterState.categoryId) qs.set('categoryId', currentFilterState.categoryId);
      if (currentFilterState.status) qs.set('status', currentFilterState.status);
      if (currentFilterState.location) qs.set('location', currentFilterState.location);

      const tbody = document.getElementById('adminComplaintsBody');
      tbody.innerHTML = '<tr><td colspan="9" class="loading-state"><span class="spinner"></span></td></tr>';

      try {
        const res = await API.request('/api/admin/complaints?' + qs.toString());
        const data = res.data;
        renderAdminComplaintsTable(data.items || [], load);
        renderAdminPagination(data.page, data.totalPages, data.totalCount, load);
      } catch (err) {
        tbody.innerHTML = '<tr><td colspan="9" class="error-state">' + API.escapeHtml(err.message) + '</td></tr>';
      }
    };

    applyBtn.addEventListener('click', () => load(1));
    searchInput.addEventListener('keydown', (e) => { if (e.key === 'Enter') load(1); });
    locInput.addEventListener('keydown', (e) => { if (e.key === 'Enter') load(1); });
    catSelect.addEventListener('change', () => load(1));
    statusSelect.addEventListener('change', () => load(1));
    resetBtn.addEventListener('click', () => {
      searchInput.value = '';
      catSelect.value = '';
      statusSelect.value = '';
      locInput.value = '';
      load(1);
    });

    bindAdminComplaintModals(load);
    load(1);
  }

  async function loadCategories() {
    const sel = document.getElementById('adminFilterCategory');
    if (!sel) return;
    const res = await API.request('/api/categories');
    const cats = res.data || [];
    sel.innerHTML = '<option value="">All Categories</option>' +
      cats.map((c) => '<option value="' + c.categoryId + '">' + API.escapeHtml(c.name) + '</option>').join('');
  }

  async function loadActiveDepartments() {
    const res = await API.request('/api/departments?activeOnly=true');
    activeDepartments = res.data || [];
    const assignSel = document.getElementById('assignDepartmentSelect');
    if (assignSel) {
      assignSel.innerHTML = '<option value="">Select Department</option>' +
        activeDepartments.map((d) => '<option value="' + d.departmentId + '">' + API.escapeHtml(d.name) + '</option>').join('');
    }
  }

  function renderAdminComplaintsTable(items) {
    const tbody = document.getElementById('adminComplaintsBody');
    if (!items.length) {
      tbody.innerHTML = '<tr><td colspan="9" class="empty-state">No complaints found matching the selected filters.</td></tr>';
      return;
    }

    tbody.innerHTML = items.map((c) => {
      const recBadge = c.hasRecurrenceAlert
        ? '<span class="badge badge-alert">' + API.escapeHtml(c.recurrenceVerificationStatus || 'Linked') + '</span>'
        : '<span style="color:var(--text-light);font-size:0.82rem;">—</span>';
      const isTerminal = c.status === 'CLOSED';
      const isResolved = c.status === 'RESOLVED' || c.status === 'CLOSED';

      return '<tr>' +
        '<td><strong>#' + c.complaintId + '</strong></td>' +
        '<td>' + API.escapeHtml(c.title) + '</td>' +
        '<td>' + API.escapeHtml(c.citizenName) + '</td>' +
        '<td>' + API.escapeHtml(c.categoryName) + '</td>' +
        '<td>' + API.escapeHtml(c.area + ', ' + c.city) + '</td>' +
        '<td>' + API.escapeHtml(c.departmentName || 'Unassigned') + '</td>' +
        '<td>' + API.statusBadge(c.status) + '</td>' +
        '<td>' + recBadge + '</td>' +
        '<td style="white-space:nowrap;">' +
          '<button type="button" class="btn btn-outline btn-sm js-view-detail" data-id="' + c.complaintId + '">Details</button> ' +
          (!isResolved ? '<button type="button" class="btn btn-navy btn-sm js-assign-dept" data-id="' + c.complaintId + '" data-dept="' + (c.departmentId || '') + '">Assign</button> ' : '') +
          (!isTerminal ? '<button type="button" class="btn btn-primary btn-sm js-update-status" data-id="' + c.complaintId + '" data-status="' + c.status + '">Update</button>' : '') +
        '</td>' +
      '</tr>';
    }).join('');

    tbody.querySelectorAll('.js-view-detail').forEach((btn) => {
      btn.addEventListener('click', () => openComplaintDetailModal(btn.dataset.id));
    });
    tbody.querySelectorAll('.js-assign-dept').forEach((btn) => {
      btn.addEventListener('click', () => openAssignModal(btn.dataset.id, btn.dataset.dept));
    });
    tbody.querySelectorAll('.js-update-status').forEach((btn) => {
      btn.addEventListener('click', () => openStatusModal(btn.dataset.id, btn.dataset.status));
    });
  }

  function renderAdminPagination(page, totalPages, totalCount, reloadFn) {
    const info = document.getElementById('adminPaginationInfo');
    const prev = document.getElementById('adminPrevPage');
    const next = document.getElementById('adminNextPage');
    if (info) info.textContent = 'Page ' + page + ' of ' + totalPages + ' (' + totalCount + ' complaints)';
    if (prev) {
      prev.disabled = page <= 1;
      prev.onclick = () => reloadFn(page - 1);
    }
    if (next) {
      next.disabled = page >= totalPages;
      next.onclick = () => reloadFn(page + 1);
    }
  }

  async function openComplaintDetailModal(complaintId) {
    const modal = document.getElementById('detailModal');
    const body = document.getElementById('detailModalBody');
    body.innerHTML = '<div class="loading-state"><span class="spinner"></span></div>';
    modal.classList.add('open');

    try {
      const res = await API.request('/api/complaints/' + complaintId);
      const c = res.data;

      const historyHtml = (c.history || []).map((h) =>
        '<div class="timeline-item">' +
          '<div class="timeline-meta">' + API.formatDate(h.updatedAt) + ' &bull; ' + API.escapeHtml(h.updatedByName) + '</div>' +
          '<div class="timeline-title">' + (h.previousStatus ? API.escapeHtml(h.previousStatus) + ' &rarr; ' : '') + API.escapeHtml(h.newStatus) + '</div>' +
          (h.remarks ? '<div class="timeline-remarks">' + API.escapeHtml(h.remarks) + '</div>' : '') +
        '</div>'
      ).join('');

      body.innerHTML =
        '<div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:12px;">' +
          '<strong>Complaint #' + c.complaintId + '</strong>' + API.statusBadge(c.status) +
        '</div>' +
        '<h4 style="margin:0 0 8px;color:var(--navy);">' + API.escapeHtml(c.title) + '</h4>' +
        '<p style="margin:0 0 12px;color:var(--text-muted);">' + API.escapeHtml(c.description) + '</p>' +
        '<div style="font-size:0.88rem;line-height:1.7;margin-bottom:14px;">' +
          '<div><strong>Citizen:</strong> ' + API.escapeHtml(c.citizenName) + ' (' + API.escapeHtml(c.citizenEmail) + ', ' + API.escapeHtml(c.citizenPhone) + ')</div>' +
          '<div><strong>Category:</strong> ' + API.escapeHtml(c.categoryName) + '</div>' +
          '<div><strong>Department:</strong> ' + API.escapeHtml(c.departmentName || 'Unassigned') + '</div>' +
          '<div><strong>Location:</strong> ' + API.escapeHtml(c.area + ', ' + c.city + ', ' + c.state) + (c.landmark ? ' (Landmark: ' + API.escapeHtml(c.landmark) + ')' : '') + '</div>' +
          '<div><strong>Submitted:</strong> ' + API.formatDate(c.submittedAt) + '</div>' +
          (c.resolvedAt ? '<div><strong>Resolved:</strong> ' + API.formatDate(c.resolvedAt) + ' — ' + API.escapeHtml(c.resolutionRemarks || '') + '</div>' : '') +
        '</div>' +
        (c.imagePath ? '<div style="margin-bottom:14px;"><strong>Submitted Evidence:</strong><br><img class="evidence-img" style="margin-top:6px;" src="' + API.url('/api/files/' + c.imagePath) + '" alt="Evidence"></div>' : '') +
        (c.resolutionImagePath ? '<div style="margin-bottom:14px;"><strong>Resolution Evidence:</strong><br><img class="evidence-img" style="margin-top:6px;" src="' + API.url('/api/files/' + c.resolutionImagePath) + '" alt="Resolution Evidence"></div>' : '') +
        '<h4 style="margin:14px 0 10px;color:var(--navy);">Status History</h4>' +
        '<div class="timeline">' + (historyHtml || '<p>No history.</p>') + '</div>';
    } catch (err) {
      body.innerHTML = '<div class="error-state">' + API.escapeHtml(err.message) + '</div>';
    }
  }

  function openAssignModal(complaintId, currentDeptId) {
    document.getElementById('assignComplaintId').value = complaintId;
    document.getElementById('assignComplaintLabel').textContent = '#' + complaintId;
    document.getElementById('assignDepartmentSelect').value = currentDeptId || '';
    document.getElementById('assignRemarks').value = '';
    document.getElementById('assignModal').classList.add('open');
  }

  function openStatusModal(complaintId, currentStatus) {
    document.getElementById('statusComplaintId').value = complaintId;
    document.getElementById('statusComplaintLabel').textContent = '#' + complaintId + ' (Current: ' + currentStatus.replace(/_/g, ' ') + ')';

    const transitions = {
      SUBMITTED: ['UNDER_REVIEW', 'ASSIGNED'],
      UNDER_REVIEW: ['ASSIGNED', 'IN_PROGRESS', 'RESOLVED'],
      ASSIGNED: ['UNDER_REVIEW', 'IN_PROGRESS', 'RESOLVED'],
      IN_PROGRESS: ['ASSIGNED', 'RESOLVED'],
      RESOLVED: ['CLOSED'],
      CLOSED: []
    };
    const allowed = transitions[currentStatus] || [];
    const sel = document.getElementById('nextStatusSelect');
    sel.innerHTML = allowed.map((st) => '<option value="' + st + '">' + st.replace(/_/g, ' ') + '</option>').join('');
    document.getElementById('statusRemarks').value = '';
    const resImg = document.getElementById('resolutionImageInput');
    if (resImg) resImg.value = '';
    toggleResolutionEvidenceField(sel.value);
    document.getElementById('statusModal').classList.add('open');
  }

  function toggleResolutionEvidenceField(statusVal) {
    const group = document.getElementById('resolutionImageGroup');
    if (group) {
      group.style.display = statusVal === 'RESOLVED' ? 'block' : 'none';
    }
  }

  function bindAdminComplaintModals(reloadFn) {
    document.querySelectorAll('[data-close-modal]').forEach((btn) => {
      btn.addEventListener('click', () => {
        const m = document.getElementById(btn.getAttribute('data-close-modal'));
        if (m) m.classList.remove('open');
      });
    });

    const nextSel = document.getElementById('nextStatusSelect');
    if (nextSel) {
      nextSel.addEventListener('change', () => toggleResolutionEvidenceField(nextSel.value));
    }

    const assignForm = document.getElementById('assignForm');
    if (assignForm) {
      assignForm.addEventListener('submit', async (e) => {
        e.preventDefault();
        const id = document.getElementById('assignComplaintId').value;
        const departmentId = Number(document.getElementById('assignDepartmentSelect').value);
        const remarks = document.getElementById('assignRemarks').value.trim();
        if (!departmentId) {
          API.showToast('Please select a department.', 'error');
          return;
        }
        try {
          await API.request('/api/admin/complaints/' + id + '/assign', {
            method: 'PUT',
            body: { departmentId, remarks }
          });
          document.getElementById('assignModal').classList.remove('open');
          API.showToast('Department assigned to Complaint #' + id, 'success');
          reloadFn(currentPage);
        } catch (err) {
          API.showToast(err.message || 'Failed to assign department.', 'error');
        }
      });
    }

    const statusForm = document.getElementById('statusForm');
    if (statusForm) {
      statusForm.addEventListener('submit', async (e) => {
        e.preventDefault();
        const id = document.getElementById('statusComplaintId').value;
        const status = document.getElementById('nextStatusSelect').value;
        const remarks = document.getElementById('statusRemarks').value.trim();
        const fileInput = document.getElementById('resolutionImageInput');
        const hasFile = fileInput && fileInput.files && fileInput.files[0];

        if (status === 'RESOLVED' && !remarks) {
          API.showToast('Resolution remarks are required when resolving a complaint.', 'error');
          return;
        }

        try {
          if (hasFile) {
            const fd = new FormData();
            fd.append('status', status);
            fd.append('remarks', remarks);
            fd.append('resolutionImage', fileInput.files[0]);
            await API.request('/api/complaints/' + id + '/status', { method: 'POST', body: fd });
          } else {
            await API.request('/api/complaints/' + id + '/status', {
              method: 'PUT',
              body: { status, remarks }
            });
          }
          document.getElementById('statusModal').classList.remove('open');
          API.showToast('Complaint #' + id + ' updated to ' + status.replace(/_/g, ' '), 'success');
          reloadFn(currentPage);
        } catch (err) {
          API.showToast(err.message || 'Failed to update status.', 'error');
        }
      });
    }
  }

  // ===================== ADMIN DEPARTMENTS PAGE =====================
  async function initAdminDepartmentsPage() {
    const user = await Auth.requireAuth('ADMIN');
    if (!user) return;

    const load = async () => {
      const tbody = document.getElementById('departmentsBody');
      tbody.innerHTML = '<tr><td colspan="6" class="loading-state"><span class="spinner"></span></td></tr>';
      try {
        const res = await API.request('/api/departments');
        renderDepartmentsTable(res.data || [], load);
      } catch (err) {
        tbody.innerHTML = '<tr><td colspan="6" class="error-state">' + API.escapeHtml(err.message) + '</td></tr>';
      }
    };

    document.getElementById('addDeptBtn').addEventListener('click', () => openDeptModal(null));
    document.querySelectorAll('[data-close-modal]').forEach((btn) => {
      btn.addEventListener('click', () => {
        document.getElementById(btn.getAttribute('data-close-modal')).classList.remove('open');
      });
    });

    document.getElementById('deptForm').addEventListener('submit', async (e) => {
      e.preventDefault();
      const id = document.getElementById('deptId').value;
      const payload = {
        name: document.getElementById('deptName').value.trim(),
        description: document.getElementById('deptDesc').value.trim(),
        contactEmail: document.getElementById('deptEmail').value.trim(),
        contactPhone: document.getElementById('deptPhone').value.trim(),
        active: document.getElementById('deptActive').checked
      };
      if (!payload.name) {
        API.showToast('Department name is required.', 'error');
        return;
      }
      try {
        if (id) {
          await API.request('/api/departments/' + id, { method: 'PUT', body: payload });
          API.showToast('Department updated.', 'success');
        } else {
          await API.request('/api/departments', { method: 'POST', body: payload });
          API.showToast('Department added.', 'success');
        }
        document.getElementById('deptModal').classList.remove('open');
        load();
      } catch (err) {
        API.showToast(err.message || 'Failed to save department.', 'error');
      }
    });

    load();
  }

  function renderDepartmentsTable(list, reloadFn) {
    const tbody = document.getElementById('departmentsBody');
    if (!list.length) {
      tbody.innerHTML = '<tr><td colspan="6" class="empty-state">No departments configured.</td></tr>';
      return;
    }
    tbody.innerHTML = list.map((d) =>
      '<tr>' +
        '<td><strong>' + API.escapeHtml(d.name) + '</strong><br><span style="font-size:0.82rem;color:var(--text-light);">' + API.escapeHtml(d.description || '') + '</span></td>' +
        '<td>' + API.escapeHtml(d.contactEmail || '—') + '</td>' +
        '<td>' + API.escapeHtml(d.contactPhone || '—') + '</td>' +
        '<td>' + (d.assignedComplaintsCount || 0) + '</td>' +
        '<td>' + (d.active ? '<span class="badge badge-active">Active</span>' : '<span class="badge badge-inactive">Inactive</span>') + '</td>' +
        '<td style="white-space:nowrap;">' +
          '<button type="button" class="btn btn-outline btn-sm js-edit-dept" data-json="' + API.escapeHtml(JSON.stringify(d)) + '">Edit</button> ' +
          (d.active
            ? '<button type="button" class="btn btn-danger btn-sm js-deact-dept" data-id="' + d.departmentId + '" data-name="' + API.escapeHtml(d.name) + '">Deactivate</button>'
            : '') +
        '</td>' +
      '</tr>'
    ).join('');

    tbody.querySelectorAll('.js-edit-dept').forEach((btn) => {
      btn.addEventListener('click', () => openDeptModal(JSON.parse(btn.dataset.json)));
    });
    tbody.querySelectorAll('.js-deact-dept').forEach((btn) => {
      btn.addEventListener('click', async () => {
        const ok = await API.showConfirmModal(
          'Deactivate Department',
          'Deactivate "' + btn.dataset.name + '"? Existing linked complaints will remain preserved.',
          'Deactivate',
          'btn-danger'
        );
        if (!ok) return;
        try {
          await API.request('/api/departments/' + btn.dataset.id, { method: 'DELETE' });
          API.showToast('Department deactivated.', 'success');
          reloadFn();
        } catch (err) {
          API.showToast(err.message || 'Failed to deactivate department.', 'error');
        }
      });
    });
  }

  function openDeptModal(dept) {
    document.getElementById('deptModalTitle').textContent = dept ? 'Edit Department' : 'Add Department';
    document.getElementById('deptId').value = dept ? dept.departmentId : '';
    document.getElementById('deptName').value = dept ? dept.name : '';
    document.getElementById('deptDesc').value = dept ? (dept.description || '') : '';
    document.getElementById('deptEmail').value = dept ? (dept.contactEmail || '') : '';
    document.getElementById('deptPhone').value = dept ? (dept.contactPhone || '') : '';
    document.getElementById('deptActive').checked = dept ? Boolean(dept.active) : true;
    document.getElementById('deptActiveGroup').style.display = dept ? 'block' : 'none';
    document.getElementById('deptModal').classList.add('open');
  }

  window.CivicAdmin = {
    initAdminDashboard,
    initAdminComplaintsPage,
    initAdminDepartmentsPage
  };
})(window);

