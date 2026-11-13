(function (window) {
  'use strict';

  const API = window.CivicAPI;
  const Auth = window.CivicAuth;

  const MAX_FILE_BYTES = 5 * 1024 * 1024;
  const ALLOWED_TYPES = ['image/jpeg', 'image/png', 'image/webp', 'image/gif'];

  let currentPage = 1;
  const pageSize = 10;

  async function loadCategoriesIntoSelect(selectId, includeAllOption) {
    const sel = document.getElementById(selectId);
    if (!sel) return [];
    const res = await API.request('/api/categories');
    const categories = res.data || [];
    const opts = includeAllOption ? ['<option value="">All Categories</option>'] : ['<option value="">Select Category</option>'];
    categories.forEach((c) => {
      opts.push('<option value="' + c.categoryId + '">' + API.escapeHtml(c.name) + '</option>');
    });
    sel.innerHTML = opts.join('');
    return categories;
  }

  async function initSubmitComplaintPage() {
    const user = await Auth.requireAuth('CITIZEN');
    if (!user) return;

    await loadCategoriesIntoSelect('categoryId', false);

    const params = new URLSearchParams(window.location.search);
    const parentIdParam = params.get('parentComplaintId');
    let parentComplaintId = parentIdParam && /^\d+$/.test(parentIdParam) ? Number(parentIdParam) : null;

    const parentSelect = document.getElementById('parentComplaintSelect');
    if (parentSelect) {
      try {
        const resolvedRes = await API.request('/api/complaints/my?status=RESOLVED_OR_CLOSED&page=1&pageSize=50');
        const resolvedList = (resolvedRes.data && resolvedRes.data.items) || [];
        parentSelect.innerHTML = '<option value="">No — This is a new civic issue</option>' +
          resolvedList.map((rc) =>
            '<option value="' + rc.complaintId + '">#' + rc.complaintId + ' — ' +
            API.escapeHtml(rc.title) + ' (' + API.escapeHtml(rc.area) + ')</option>'
          ).join('');
      } catch (_) {
        // Non-blocking if citizen has no resolved complaints yet
      }
      parentSelect.addEventListener('change', async () => {
        const selected = parentSelect.value;
        parentComplaintId = selected ? Number(selected) : null;
        await applyParentComplaintPrefill(parentComplaintId);
      });
    }

    if (parentComplaintId) {
      if (parentSelect) parentSelect.value = String(parentComplaintId);
      await applyParentComplaintPrefill(parentComplaintId);
    }

    async function applyParentComplaintPrefill(origId) {
      const banner = document.getElementById('recurrenceParentBanner');
      if (!origId) {
        if (banner) {
          banner.style.display = 'none';
          banner.innerHTML = '';
        }
        return;
      }
      try {
        const res = await API.request('/api/complaints/' + origId);
        const orig = res.data;
        if (banner) {
          banner.style.display = 'block';
          banner.innerHTML =
            '<strong>Reporting Recurring Issue:</strong> Linking this new report to previously resolved Complaint <strong>#' +
            orig.complaintId + '</strong> (' + API.escapeHtml(orig.title) + ').';
        }
        document.getElementById('categoryId').value = String(orig.categoryId);
        document.getElementById('area').value = orig.area || '';
        document.getElementById('city').value = orig.city || '';
        document.getElementById('state').value = orig.state || '';
        document.getElementById('landmark').value = orig.landmark || '';
        if (orig.latitude != null) document.getElementById('latitude').value = orig.latitude;
        if (orig.longitude != null) document.getElementById('longitude').value = orig.longitude;
      } catch (err) {
        API.showToast(err.message || 'Unable to load original complaint.', 'error');
        parentComplaintId = null;
      }
    }

    // Image validation & preview
    const fileInput = document.getElementById('imageInput');
    const previewBox = document.getElementById('imagePreviewBox');
    const previewImg = document.getElementById('imagePreviewImg');
    const previewMeta = document.getElementById('imagePreviewMeta');
    const clearImgBtn = document.getElementById('clearImageBtn');

    if (fileInput) {
      fileInput.addEventListener('change', () => {
        const file = fileInput.files && fileInput.files[0];
        if (!file) {
          previewBox.classList.remove('show');
          return;
        }
        if (!ALLOWED_TYPES.includes(file.type)) {
          API.showToast('Invalid image format. Please choose JPG, PNG, WEBP, or GIF.', 'error');
          fileInput.value = '';
          previewBox.classList.remove('show');
          return;
        }
        if (file.size > MAX_FILE_BYTES) {
          API.showToast('Image size exceeds the 5 MB limit.', 'error');
          fileInput.value = '';
          previewBox.classList.remove('show');
          return;
        }

        const reader = new FileReader();
        reader.onload = (ev) => {
          previewImg.src = ev.target.result;
          previewMeta.textContent = file.name + ' (' + (file.size / 1024).toFixed(1) + ' KB)';
          previewBox.classList.add('show');
        };
        reader.readAsDataURL(file);
      });
    }

    if (clearImgBtn) {
      clearImgBtn.addEventListener('click', () => {
        fileInput.value = '';
        previewBox.classList.remove('show');
      });
    }

    // Geolocation helper
    const geoBtn = document.getElementById('detectLocationBtn');
    if (geoBtn) {
      geoBtn.addEventListener('click', () => {
        if (!navigator.geolocation) {
          API.showToast('Geolocation is not supported by your browser.', 'warning');
          return;
        }
        geoBtn.disabled = true;
        geoBtn.textContent = 'Detecting...';
        navigator.geolocation.getCurrentPosition(
          (pos) => {
            document.getElementById('latitude').value = pos.coords.latitude.toFixed(6);
            document.getElementById('longitude').value = pos.coords.longitude.toFixed(6);
            geoBtn.disabled = false;
            geoBtn.textContent = 'Use My GPS Location';
            API.showToast('Coordinates populated.', 'success');
          },
          () => {
            geoBtn.disabled = false;
            geoBtn.textContent = 'Use My GPS Location';
            API.showToast('Unable to retrieve GPS coordinates.', 'warning');
          },
          { timeout: 8000 }
        );
      });
    }

    const form = document.getElementById('submitComplaintForm');
    form.addEventListener('submit', async (e) => {
      e.preventDefault();

      const categoryId = document.getElementById('categoryId').value;
      const title = document.getElementById('title').value.trim();
      const description = document.getElementById('description').value.trim();
      const area = document.getElementById('area').value.trim();
      const city = document.getElementById('city').value.trim();
      const state = document.getElementById('state').value.trim();
      const landmark = document.getElementById('landmark').value.trim();
      const latitude = document.getElementById('latitude').value.trim();
      const longitude = document.getElementById('longitude').value.trim();

      if (!categoryId || title.length < 5 || description.length < 15 || !area || !city || !state) {
        API.showToast('Please complete all required fields with valid details.', 'error');
        return;
      }
      if ((latitude && !longitude) || (!latitude && longitude)) {
        API.showToast('Please enter both latitude and longitude, or leave both empty.', 'error');
        return;
      }

      const fd = new FormData();
      fd.append('categoryId', categoryId);
      fd.append('title', title);
      fd.append('description', description);
      fd.append('area', area);
      fd.append('city', city);
      fd.append('state', state);
      if (landmark) fd.append('landmark', landmark);
      if (latitude && longitude) {
        fd.append('latitude', latitude);
        fd.append('longitude', longitude);
      }
      if (fileInput && fileInput.files && fileInput.files[0]) {
        fd.append('image', fileInput.files[0]);
      }

      const submitBtn = document.getElementById('submitComplaintBtn');
      submitBtn.disabled = true;
      submitBtn.textContent = 'Submitting...';

      try {
        const endpoint = parentComplaintId
          ? '/api/complaints/' + parentComplaintId + '/recurrence'
          : '/api/complaints';
        const res = await API.request(endpoint, { method: 'POST', body: fd });
        const created = res.data;
        if (created.hasRecurrenceAlert) {
          API.showToast('Complaint #' + created.complaintId + ' submitted. Possible recurring issue detected!', 'warning');
        } else {
          API.showToast('Complaint #' + created.complaintId + ' submitted successfully!', 'success');
        }
        window.location.href = API.url('/citizen/complaint-details.html?id=' + created.complaintId);
      } catch (err) {
        API.showToast(err.message || 'Failed to submit complaint.', 'error');
        submitBtn.disabled = false;
        submitBtn.textContent = 'Submit Complaint';
      }
    });
  }

  async function initMyComplaintsPage() {
    const user = await Auth.requireAuth('CITIZEN');
    if (!user) return;

    await loadCategoriesIntoSelect('filterCategory', true);

    const searchInput = document.getElementById('filterSearch');
    const catSelect = document.getElementById('filterCategory');
    const statusSelect = document.getElementById('filterStatus');
    const applyBtn = document.getElementById('applyFiltersBtn');
    const resetBtn = document.getElementById('resetFiltersBtn');

    const load = async (page = 1) => {
      currentPage = page;
      const qs = new URLSearchParams({
        page: String(currentPage),
        pageSize: String(pageSize)
      });
      if (searchInput.value.trim()) qs.set('search', searchInput.value.trim());
      if (catSelect.value) qs.set('categoryId', catSelect.value);
      if (statusSelect.value) qs.set('status', statusSelect.value);

      const tbody = document.getElementById('myComplaintsBody');
      tbody.innerHTML = '<tr><td colspan="9" class="loading-state"><span class="spinner"></span></td></tr>';

      try {
        const res = await API.request('/api/complaints/my?' + qs.toString());
        const data = res.data;
        renderMyComplaintsTable(data.items || []);
        renderPagination(data.page, data.totalPages, data.totalCount, load);
      } catch (err) {
        tbody.innerHTML = '<tr><td colspan="9" class="error-state">' + API.escapeHtml(err.message) + '</td></tr>';
      }
    };

    applyBtn.addEventListener('click', () => load(1));
    searchInput.addEventListener('keydown', (e) => {
      if (e.key === 'Enter') load(1);
    });
    catSelect.addEventListener('change', () => load(1));
    statusSelect.addEventListener('change', () => load(1));
    resetBtn.addEventListener('click', () => {
      searchInput.value = '';
      catSelect.value = '';
      statusSelect.value = '';
      load(1);
    });

    load(1);
  }

  function renderMyComplaintsTable(items) {
    const tbody = document.getElementById('myComplaintsBody');
    if (!items.length) {
      tbody.innerHTML = '<tr><td colspan="9" class="empty-state">No complaints match your current filters.</td></tr>';
      return;
    }

    tbody.innerHTML = items.map((c) => {
      const canReportRecurrence = c.status === 'RESOLVED' || c.status === 'CLOSED';
      let recIndicator = '<span style="color:var(--text-light);font-size:0.82rem;">None</span>';
      if (c.recurrenceVerificationStatus === 'CONFIRMED') {
        recIndicator = '<span class="badge badge-confirmed">Confirmed Recurrence</span>';
      } else if (c.recurrenceVerificationStatus === 'PENDING') {
        recIndicator = '<span class="badge badge-pending">Candidate (' + (c.recurrenceScore || 0) + ')</span>';
      } else if (c.childRecurrenceCount > 0) {
        recIndicator = '<span class="badge badge-alert">' + c.childRecurrenceCount + ' Linked</span>';
      }

      const actions =
        '<a class="btn btn-outline btn-sm" href="' + API.url('/citizen/complaint-details.html?id=' + c.complaintId) + '">View</a>' +
        (canReportRecurrence
          ? ' <a class="btn btn-navy btn-sm" href="' + API.url('/citizen/submit-complaint.html?parentComplaintId=' + c.complaintId) + '">Report Again</a>'
          : '');

      return '<tr>' +
        '<td><strong>#' + c.complaintId + '</strong></td>' +
        '<td>' + API.escapeHtml(c.title) + '</td>' +
        '<td>' + API.escapeHtml(c.categoryName) + '</td>' +
        '<td>' + API.escapeHtml(c.area + ', ' + c.city) + '</td>' +
        '<td>' + API.formatDate(c.submittedAt) + '</td>' +
        '<td>' + API.escapeHtml(c.departmentName || 'Unassigned') + '</td>' +
        '<td>' + API.statusBadge(c.status) + '</td>' +
        '<td>' + recIndicator + '</td>' +
        '<td style="white-space:nowrap;">' + actions + '</td>' +
      '</tr>';
    }).join('');
  }

  function renderPagination(page, totalPages, totalCount, onPageChange) {
    const infoEl = document.getElementById('paginationInfo');
    const prevBtn = document.getElementById('prevPageBtn');
    const nextBtn = document.getElementById('nextPageBtn');
    if (infoEl) {
      infoEl.textContent = 'Page ' + page + ' of ' + totalPages + ' (' + totalCount + ' total records)';
    }
    if (prevBtn) {
      prevBtn.disabled = page <= 1;
      prevBtn.onclick = () => onPageChange(page - 1);
    }
    if (nextBtn) {
      nextBtn.disabled = page >= totalPages;
      nextBtn.onclick = () => onPageChange(page + 1);
    }
  }

  async function initComplaintDetailsPage() {
    const user = await Auth.requireAuth('CITIZEN');
    if (!user) return;

    const params = new URLSearchParams(window.location.search);
    const id = params.get('id');
    const container = document.getElementById('detailsContainer');

    if (!id || !/^\d+$/.test(id)) {
      container.innerHTML = '<div class="error-state">Invalid complaint ID specified.</div>';
      return;
    }

    try {
      const [detailRes, relRes] = await Promise.all([
        API.request('/api/complaints/' + id),
        API.request('/api/complaints/' + id + '/recurrences')
      ]);
      renderComplaintDetails(detailRes.data, relRes.data || []);
    } catch (err) {
      container.innerHTML = '<div class="error-state">' + API.escapeHtml(err.message) + '</div>';
    }
  }

  function renderComplaintDetails(c, related) {
    const container = document.getElementById('detailsContainer');
    const stages = ['SUBMITTED', 'UNDER_REVIEW', 'ASSIGNED', 'IN_PROGRESS', 'RESOLVED', 'CLOSED'];
    const currentIdx = stages.indexOf(c.status);

    const stepperHtml = stages.map((st, idx) => {
      let cls = 'step-pill';
      if (idx < currentIdx) cls += ' done';
      else if (idx === currentIdx) cls += ' current';
      return '<div class="' + cls + '">' + st.replace(/_/g, ' ') + '</div>';
    }).join('');

    const historyHtml = (c.history && c.history.length)
      ? c.history.map((h) =>
          '<div class="timeline-item">' +
            '<div class="timeline-meta">' + API.formatDate(h.updatedAt) + ' &bull; ' +
              API.escapeHtml(h.updatedByName) + ' (' + API.escapeHtml(h.updatedByRole) + ')</div>' +
            '<div class="timeline-title">' +
              (h.previousStatus ? API.escapeHtml(h.previousStatus.replace(/_/g, ' ')) + ' &rarr; ' : '') +
              API.escapeHtml(h.newStatus.replace(/_/g, ' ')) +
            '</div>' +
            (h.remarks ? '<div class="timeline-remarks">' + API.escapeHtml(h.remarks) + '</div>' : '') +
          '</div>'
        ).join('')
      : '<div class="empty-state">No status history recorded.</div>';

    const evidenceHtml = c.imagePath
      ? '<img class="evidence-img" src="' + API.url('/api/files/' + c.imagePath) + '" alt="Submitted evidence image">'
      : '<p style="color:var(--text-light);">No evidence image uploaded.</p>';

    const resEvidenceHtml = c.resolutionImagePath
      ? '<div style="margin-top:12px;"><strong>Resolution Evidence:</strong><br><img class="evidence-img" style="margin-top:8px;" src="' +
        API.url('/api/files/' + c.resolutionImagePath) + '" alt="Resolution evidence image"></div>'
      : '';

    const recRecordsHtml = (c.recurrenceRecords && c.recurrenceRecords.length)
      ? c.recurrenceRecords.map((rr) =>
          '<div class="recurrence-box" style="margin-bottom:10px;">' +
            '<div style="display:flex;justify-content:space-between;align-items:center;gap:8px;margin-bottom:6px;">' +
              '<strong>Original #' + rr.originalComplaintId + ' &rarr; Recurring #' + rr.recurringComplaintId + '</strong>' +
              '<span class="score-badge">Match Score: ' + rr.matchingScore + '/100</span>' +
            '</div>' +
            '<p style="margin:0 0 6px;font-size:0.88rem;color:var(--text-muted);">' + API.escapeHtml(rr.matchingReason) + '</p>' +
            '<div>' + API.statusBadge(rr.verificationStatus) + '</div>' +
          '</div>'
        ).join('')
      : '<p style="color:var(--text-light);margin:0;">No recurrence records linked to this complaint.</p>';

    const relatedHtml = related.length
      ? related.map((rel) =>
          '<tr>' +
            '<td><strong>#' + rel.complaintId + '</strong></td>' +
            '<td>' + API.escapeHtml(rel.title) + '</td>' +
            '<td>' + API.statusBadge(rel.status) + '</td>' +
            '<td>' + API.formatDate(rel.submittedAt) + '</td>' +
            '<td><a class="btn btn-outline btn-sm" href="' + API.url('/citizen/complaint-details.html?id=' + rel.complaintId) + '">Open</a></td>' +
          '</tr>'
        ).join('')
      : '<tr><td colspan="5" class="empty-state">No related recurring complaints found.</td></tr>';

    const canReportAgain = c.status === 'RESOLVED' || c.status === 'CLOSED';
    const reportAgainBtn = canReportAgain
      ? '<a class="btn btn-primary" href="' + API.url('/citizen/submit-complaint.html?parentComplaintId=' + c.complaintId) + '">Report Issue Occurred Again</a>'
      : '';

    container.innerHTML =
      '<div class="panel">' +
        '<div class="panel-header">' +
          '<div>' +
            '<span style="font-size:0.84rem;color:var(--text-light);">Complaint #' + c.complaintId + '</span>' +
            '<h2 style="margin-top:2px;">' + API.escapeHtml(c.title) + '</h2>' +
          '</div>' +
          '<div style="display:flex;gap:10px;align-items:center;flex-wrap:wrap;">' +
            API.statusBadge(c.status) +
            reportAgainBtn +
          '</div>' +
        '</div>' +
        '<div class="panel-body">' +
          '<div class="status-stepper">' + stepperHtml + '</div>' +
          '<div class="dash-grid-2">' +
            '<div>' +
              '<h3 style="margin:0 0 10px;color:var(--navy);">Issue Details</h3>' +
              '<p style="margin:0 0 14px;">' + API.escapeHtml(c.description) + '</p>' +
              '<p style="margin:0 0 6px;"><strong>Category:</strong> ' + API.escapeHtml(c.categoryName) + '</p>' +
              '<p style="margin:0 0 6px;"><strong>Assigned Department:</strong> ' + API.escapeHtml(c.departmentName || 'Pending Assignment') + '</p>' +
              '<p style="margin:0 0 6px;"><strong>Location:</strong> ' + API.escapeHtml(c.area + ', ' + c.city + ', ' + c.state) + '</p>' +
              (c.landmark ? '<p style="margin:0 0 6px;"><strong>Landmark:</strong> ' + API.escapeHtml(c.landmark) + '</p>' : '') +
              (c.latitude != null ? '<p style="margin:0 0 6px;"><strong>Coordinates:</strong> ' + c.latitude + ', ' + c.longitude + '</p>' : '') +
              '<p style="margin:0;"><strong>Submitted:</strong> ' + API.formatDate(c.submittedAt) + '</p>' +
            '</div>' +
            '<div>' +
              '<h3 style="margin:0 0 10px;color:var(--navy);">Uploaded Evidence</h3>' +
              evidenceHtml +
            '</div>' +
          '</div>' +
          (c.resolvedAt || c.resolutionRemarks
            ? '<div style="margin-top:20px;padding:16px;background:var(--green-soft);border:1px solid #86EFAC;border-radius:var(--radius-sm);">' +
                '<h3 style="margin:0 0 8px;color:#166534;">Resolution Summary</h3>' +
                '<p style="margin:0 0 4px;"><strong>Resolved At:</strong> ' + API.formatDate(c.resolvedAt) + '</p>' +
                '<p style="margin:0 0 4px;"><strong>Verified By:</strong> ' + API.escapeHtml(c.resolvedByName || 'Administrator') + '</p>' +
                '<p style="margin:0;"><strong>Remarks:</strong> ' + API.escapeHtml(c.resolutionRemarks || '—') + '</p>' +
                resEvidenceHtml +
              '</div>'
            : '') +
        '</div>' +
      '</div>' +
      '<div class="dash-grid-2">' +
        '<div class="panel">' +
          '<div class="panel-header"><h3>Status Timeline</h3></div>' +
          '<div class="panel-body"><div class="timeline">' + historyHtml + '</div></div>' +
        '</div>' +
        '<div class="panel">' +
          '<div class="panel-header"><h3>Recurrence Detection</h3></div>' +
          '<div class="panel-body">' + recRecordsHtml + '</div>' +
        '</div>' +
      '</div>' +
      '<div class="panel">' +
        '<div class="panel-header"><h3>Related Recurring Complaints</h3></div>' +
        '<div class="table-wrap"><table class="data-table">' +
          '<thead><tr><th>ID</th><th>Title</th><th>Status</th><th>Submitted</th><th>Action</th></tr></thead>' +
          '<tbody>' + relatedHtml + '</tbody>' +
        '</table></div>' +
      '</div>';
  }

  window.CivicComplaints = {
    initSubmitComplaintPage,
    initMyComplaintsPage,
    initComplaintDetailsPage
  };
})(window);

