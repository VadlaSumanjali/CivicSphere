package com.civicsphere.service;

import com.civicsphere.config.AppProperties;
import com.civicsphere.dto.ComplaintDTO;
import com.civicsphere.dto.ComplaintHistoryDTO;
import com.civicsphere.dto.PagedResponse;
import com.civicsphere.dto.RecurrenceDTO;
import com.civicsphere.model.Category;
import com.civicsphere.model.Complaint;
import com.civicsphere.model.ComplaintHistory;
import com.civicsphere.model.Department;
import com.civicsphere.model.RecurrenceRecord;
import com.civicsphere.model.User;
import com.civicsphere.repository.CategoryRepository;
import com.civicsphere.repository.ComplaintHistoryRepository;
import com.civicsphere.repository.ComplaintRepository;
import com.civicsphere.repository.DepartmentRepository;
import com.civicsphere.repository.RecurrenceRecordRepository;
import com.civicsphere.repository.UserRepository;
import com.civicsphere.util.FileUploadUtil;
import com.civicsphere.util.ValidationUtil;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class ComplaintService {

    private final ComplaintRepository complaintRepository;
    private final ComplaintHistoryRepository historyRepository;
    private final CategoryRepository categoryRepository;
    private final DepartmentRepository departmentRepository;
    private final RecurrenceRecordRepository recurrenceRepository;
    private final UserRepository userRepository;
    private final RecurrenceService recurrenceService;
    private final AppProperties appProperties;

    public ComplaintService(ComplaintRepository complaintRepository,
                            ComplaintHistoryRepository historyRepository,
                            CategoryRepository categoryRepository,
                            DepartmentRepository departmentRepository,
                            RecurrenceRecordRepository recurrenceRepository,
                            UserRepository userRepository,
                            RecurrenceService recurrenceService,
                            AppProperties appProperties) {
        this.complaintRepository = complaintRepository;
        this.historyRepository = historyRepository;
        this.categoryRepository = categoryRepository;
        this.departmentRepository = departmentRepository;
        this.recurrenceRepository = recurrenceRepository;
        this.userRepository = userRepository;
        this.recurrenceService = recurrenceService;
        this.appProperties = appProperties;
    }

    @Transactional(readOnly = true)
    public List<Category> listActiveCategories() {
        return categoryRepository.findByActiveTrueOrderByNameAsc();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getPublicOverview() {
        Map<String, Object> overview = new LinkedHashMap<>();
        overview.put("categories", listActiveCategories());

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalComplaints", complaintRepository.count());
        stats.put("resolvedComplaints", complaintRepository.countByStatusIn(
                List.of(Complaint.STATUS_RESOLVED, Complaint.STATUS_CLOSED)));
        stats.put("activeDepartments", departmentRepository.countByActiveTrue());
        stats.put("recurrenceAlerts", recurrenceRepository.countByVerificationStatusNot(
                RecurrenceRecord.STATUS_REJECTED));
        overview.put("stats", stats);
        return overview;
    }

    @Transactional
    public ComplaintDTO submitComplaint(Integer userId, Integer categoryId, String title, String description,
                                        String area, String city, String state, String landmark,
                                        Double latitude, Double longitude, MultipartFile imageFile,
                                        Integer parentComplaintId) {
        String cleanTitle = ValidationUtil.clean(title);
        String cleanDesc = ValidationUtil.clean(description);
        String cleanArea = ValidationUtil.clean(area);
        String cleanCity = ValidationUtil.clean(city);
        String cleanState = ValidationUtil.clean(state);
        String cleanLandmark = ValidationUtil.clean(landmark);

        if (categoryId == null || categoryId <= 0) {
            throw new ServiceException(400, "Please select a valid complaint category.");
        }
        if (ValidationUtil.isBlank(cleanTitle) || cleanTitle.length() < 5 || cleanTitle.length() > 180) {
            throw new ServiceException(400, "Title must be between 5 and 180 characters.");
        }
        if (ValidationUtil.isBlank(cleanDesc) || cleanDesc.length() < 15 || cleanDesc.length() > 4000) {
            throw new ServiceException(400, "Description must be between 15 and 4000 characters.");
        }
        if (ValidationUtil.isBlank(cleanArea) || cleanArea.length() < 2 || cleanArea.length() > 120) {
            throw new ServiceException(400, "Area / locality must be between 2 and 120 characters.");
        }
        if (ValidationUtil.isBlank(cleanCity) || cleanCity.length() < 2 || cleanCity.length() > 80) {
            throw new ServiceException(400, "City must be between 2 and 80 characters.");
        }
        if (ValidationUtil.isBlank(cleanState) || cleanState.length() < 2 || cleanState.length() > 80) {
            throw new ServiceException(400, "State must be between 2 and 80 characters.");
        }
        if (cleanLandmark != null && cleanLandmark.length() > 180) {
            throw new ServiceException(400, "Landmark cannot exceed 180 characters.");
        }
        if (!ValidationUtil.isValidCoordinates(latitude, longitude)) {
            throw new ServiceException(400, "Invalid GPS coordinates. Provide both valid latitude and longitude or leave both blank.");
        }

        User reporter = userRepository.findById(userId)
                .orElseThrow(() -> new ServiceException(404, "Reporter account not found."));

        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new ServiceException(400, "Selected complaint category does not exist."));
        if (!category.isActive()) {
            throw new ServiceException(400, "Selected complaint category is inactive.");
        }

        Complaint parentComplaint = null;
        if (parentComplaintId != null) {
            parentComplaint = complaintRepository.findById(parentComplaintId)
                    .orElseThrow(() -> new ServiceException(404, "Original complaint referenced for recurrence was not found."));
            if (!Complaint.STATUS_RESOLVED.equals(parentComplaint.getStatus())
                    && !Complaint.STATUS_CLOSED.equals(parentComplaint.getStatus())) {
                throw new ServiceException(400, "Only RESOLVED or CLOSED complaints can be flagged for recurrence.");
            }
        }

        String uploadedPath = null;
        if (imageFile != null && !imageFile.isEmpty()) {
            try {
                uploadedPath = FileUploadUtil.saveImage(
                        imageFile, "complaints", appProperties.getUploadDir(), appProperties.getMaxUploadBytes());
            } catch (IllegalArgumentException ex) {
                throw new ServiceException(400, ex.getMessage());
            } catch (IOException ex) {
                throw new ServiceException(500, "Failed to save uploaded image.", ex);
            }
        }

        try {
            Complaint complaint = new Complaint();
            complaint.setUser(reporter);
            complaint.setCategory(category);
            complaint.setParentComplaint(parentComplaint);
            complaint.setTitle(cleanTitle);
            complaint.setDescription(cleanDesc);
            complaint.setArea(cleanArea);
            complaint.setCity(cleanCity);
            complaint.setState(cleanState);
            complaint.setLandmark(cleanLandmark);
            complaint.setLatitude(latitude);
            complaint.setLongitude(longitude);
            complaint.setImagePath(uploadedPath);
            complaint.setStatus(Complaint.STATUS_SUBMITTED);

            Complaint saved = complaintRepository.save(complaint);

            ComplaintHistory initialHistory = new ComplaintHistory();
            initialHistory.setComplaint(saved);
            initialHistory.setPreviousStatus(null);
            initialHistory.setNewStatus(Complaint.STATUS_SUBMITTED);
            initialHistory.setRemarks(parentComplaint != null
                    ? "Citizen submitted recurrence report linked to Complaint #" + parentComplaint.getComplaintId() + "."
                    : "Complaint submitted by citizen.");
            initialHistory.setUpdatedByUser(reporter);
            historyRepository.save(initialHistory);

            if (parentComplaint != null) {
                recurrenceService.createManualRecurrenceLink(parentComplaint, saved, reporter);
            }
            recurrenceService.detectAndRecordRecurrences(saved, reporter);

            return buildDetailedDto(saved);
        } catch (RuntimeException ex) {
            if (uploadedPath != null) {
                FileUploadUtil.deleteQuietly(appProperties.getUploadDir(), uploadedPath);
            }
            throw ex;
        }
    }

    @Transactional(readOnly = true)
    public PagedResponse<ComplaintDTO> listCitizenComplaints(Integer userId, String search, Integer categoryId,
                                                             String status, int page, int pageSize) {
        return findFilteredComplaints(userId, search, categoryId, status, null, page, pageSize);
    }

    @Transactional(readOnly = true)
    public PagedResponse<ComplaintDTO> listAllComplaints(String search, Integer categoryId, String status,
                                                         String location, int page, int pageSize) {
        return findFilteredComplaints(null, search, categoryId, status, location, page, pageSize);
    }

    private PagedResponse<ComplaintDTO> findFilteredComplaints(Integer userId, String search, Integer categoryId,
                                                               String status, String location, int page, int pageSize) {
        int safePage = Math.max(1, page);
        int safeSize = Math.clamp(pageSize, 1, 50);

        String cleanStatus = null;
        if (!ValidationUtil.isBlank(status) && !"ALL".equalsIgnoreCase(status.trim())) {
            cleanStatus = status.trim().toUpperCase();
            if (!"RESOLVED_OR_CLOSED".equals(cleanStatus) && !ValidationUtil.isValidComplaintStatus(cleanStatus)) {
                throw new ServiceException(400, "Invalid complaint status filter.");
            }
        }

        final String finalStatus = cleanStatus;
        final String cleanSearch = ValidationUtil.clean(search);
        final String cleanLocation = ValidationUtil.clean(location);

        Specification<Complaint> spec = (root, query, cb) -> {
            if (query != null && query.getResultType() != Long.class && query.getResultType() != long.class) {
                root.fetch("user", JoinType.LEFT);
                root.fetch("category", JoinType.LEFT);
                root.fetch("department", JoinType.LEFT);
                root.fetch("parentComplaint", JoinType.LEFT);
            }

            List<Predicate> predicates = new ArrayList<>();
            if (userId != null) {
                predicates.add(cb.equal(root.get("user").get("userId"), userId));
            }
            if (categoryId != null && categoryId > 0) {
                predicates.add(cb.equal(root.get("category").get("categoryId"), categoryId));
            }
            if (finalStatus != null) {
                if ("RESOLVED_OR_CLOSED".equals(finalStatus)) {
                    predicates.add(root.get("status").in(Complaint.STATUS_RESOLVED, Complaint.STATUS_CLOSED));
                } else {
                    predicates.add(cb.equal(root.get("status"), finalStatus));
                }
            }
            if (!ValidationUtil.isBlank(cleanSearch)) {
                String pattern = "%" + cleanSearch.toLowerCase() + "%";
                List<Predicate> searchPredicates = new ArrayList<>();
                searchPredicates.add(cb.like(cb.lower(root.get("title")), pattern));
                searchPredicates.add(cb.like(cb.lower(root.get("description")), pattern));
                searchPredicates.add(cb.like(cb.lower(root.get("area")), pattern));
                searchPredicates.add(cb.like(cb.lower(root.get("city")), pattern));
                searchPredicates.add(cb.like(cb.lower(cb.coalesce(root.get("landmark"), "")), pattern));
                if (cleanSearch.matches("\\d+")) {
                    try {
                        searchPredicates.add(cb.equal(root.get("complaintId"), Integer.parseInt(cleanSearch)));
                    } catch (NumberFormatException ignored) {
                    }
                }
                predicates.add(cb.or(searchPredicates.toArray(new Predicate[0])));
            }
            if (!ValidationUtil.isBlank(cleanLocation)) {
                String locPattern = "%" + cleanLocation.toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("area")), locPattern),
                        cb.like(cb.lower(root.get("city")), locPattern),
                        cb.like(cb.lower(root.get("state")), locPattern),
                        cb.like(cb.lower(cb.coalesce(root.get("landmark"), "")), locPattern)
                ));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Pageable pageable = PageRequest.of(
                safePage - 1, safeSize,
                Sort.by(Sort.Direction.DESC, "submittedAt").and(Sort.by(Sort.Direction.DESC, "complaintId"))
        );
        Page<Complaint> resultPage = complaintRepository.findAll(spec, pageable);
        List<ComplaintDTO> enriched = enrichDtos(resultPage.getContent());
        return new PagedResponse<>(enriched, resultPage.getTotalElements(), safePage, safeSize);
    }

    @Transactional(readOnly = true)
    public ComplaintDTO getComplaintDetails(Integer complaintId, User currentUser) {
        Complaint complaint = complaintRepository.findDetailedByComplaintId(complaintId)
                .orElseThrow(() -> new ServiceException(404, "Complaint not found."));

        if (!User.ROLE_ADMIN.equals(currentUser.getRole())
                && !Objects.equals(complaint.getUser().getUserId(), currentUser.getUserId())) {
            throw new ServiceException(403, "You are not authorized to view this complaint.");
        }
        return buildDetailedDto(complaint);
    }

    @Transactional(readOnly = true)
    public List<ComplaintDTO> getRelatedRecurrences(Integer complaintId, User currentUser) {
        Complaint complaint = complaintRepository.findDetailedByComplaintId(complaintId)
                .orElseThrow(() -> new ServiceException(404, "Complaint not found."));

        if (!User.ROLE_ADMIN.equals(currentUser.getRole())
                && !Objects.equals(complaint.getUser().getUserId(), currentUser.getUserId())) {
            throw new ServiceException(403, "You are not authorized to view related complaints.");
        }

        List<Complaint> related = complaintRepository.findRelatedRecurrences(complaintId);
        if (!User.ROLE_ADMIN.equals(currentUser.getRole())) {
            related = related.stream()
                    .filter(c -> Objects.equals(c.getUser().getUserId(), currentUser.getUserId())
                            || Objects.equals(c.getComplaintId(), complaintId)
                            || (complaint.getParentComplaint() != null
                                && Objects.equals(c.getComplaintId(), complaint.getParentComplaint().getComplaintId())))
                    .toList();
        }
        return enrichDtos(related);
    }

    @Transactional
    public ComplaintDTO assignDepartment(Integer complaintId, Integer departmentId, String remarks, Integer adminUserId) {
        if (departmentId == null || departmentId <= 0) {
            throw new ServiceException(400, "Valid departmentId is required.");
        }

        Complaint complaint = complaintRepository.findDetailedByComplaintId(complaintId)
                .orElseThrow(() -> new ServiceException(404, "Complaint not found."));
        Department department = departmentRepository.findById(departmentId)
                .orElseThrow(() -> new ServiceException(404, "Selected department was not found."));
        if (!department.isActive()) {
            throw new ServiceException(400, "Cannot assign complaint to an inactive department.");
        }
        User admin = userRepository.findById(adminUserId)
                .orElseThrow(() -> new ServiceException(404, "Administrator user not found."));

        if (Complaint.STATUS_RESOLVED.equals(complaint.getStatus())
                || Complaint.STATUS_CLOSED.equals(complaint.getStatus())) {
            throw new ServiceException(400, "Cannot reassign department after a complaint is " + complaint.getStatus() + ".");
        }

        String previousStatus = complaint.getStatus();
        String nextStatus = Complaint.STATUS_IN_PROGRESS.equals(previousStatus)
                ? Complaint.STATUS_IN_PROGRESS
                : Complaint.STATUS_ASSIGNED;

        complaint.setDepartment(department);
        complaint.setStatus(nextStatus);
        Complaint saved = complaintRepository.save(complaint);

        String cleanRemarks = ValidationUtil.clean(remarks);
        if (ValidationUtil.isBlank(cleanRemarks)) {
            cleanRemarks = "Assigned to " + department.getName() + ".";
        } else {
            cleanRemarks = "Assigned to " + department.getName() + ": " + cleanRemarks;
        }

        ComplaintHistory history = new ComplaintHistory();
        history.setComplaint(saved);
        history.setPreviousStatus(previousStatus);
        history.setNewStatus(nextStatus);
        history.setRemarks(cleanRemarks);
        history.setUpdatedByUser(admin);
        historyRepository.save(history);

        return buildDetailedDto(saved);
    }

    @Transactional
    public ComplaintDTO updateStatus(Integer complaintId, String requestedStatus, String remarks,
                                     MultipartFile resolutionImageFile, Integer adminUserId) {
        String nextStatus = requestedStatus == null ? "" : requestedStatus.trim().toUpperCase();
        if (!ValidationUtil.isValidComplaintStatus(nextStatus)) {
            throw new ServiceException(400, "Invalid complaint status value.");
        }

        Complaint complaint = complaintRepository.findDetailedByComplaintId(complaintId)
                .orElseThrow(() -> new ServiceException(404, "Complaint not found."));
        User admin = userRepository.findById(adminUserId)
                .orElseThrow(() -> new ServiceException(404, "Administrator user not found."));

        String currentStatus = complaint.getStatus();
        if (currentStatus.equals(nextStatus)) {
            throw new ServiceException(400, "Complaint is already in " + nextStatus + " status.");
        }
        if (!ValidationUtil.isValidStatusTransition(currentStatus, nextStatus)) {
            throw new ServiceException(400,
                    "Invalid status transition from " + currentStatus + " to " + nextStatus + ".");
        }
        if (Complaint.STATUS_ASSIGNED.equals(nextStatus) && complaint.getDepartment() == null) {
            throw new ServiceException(400, "Assign a department before setting status to ASSIGNED.");
        }

        String cleanRemarks = ValidationUtil.clean(remarks);
        if (Complaint.STATUS_RESOLVED.equals(nextStatus) && ValidationUtil.isBlank(cleanRemarks)) {
            throw new ServiceException(400, "Resolution remarks are required when resolving a complaint.");
        }
        if (ValidationUtil.isBlank(cleanRemarks)) {
            cleanRemarks = "Status updated from " + currentStatus + " to " + nextStatus + ".";
        }

        String resolutionImgPath = null;
        if (resolutionImageFile != null && !resolutionImageFile.isEmpty()) {
            try {
                resolutionImgPath = FileUploadUtil.saveImage(
                        resolutionImageFile, "resolutions",
                        appProperties.getUploadDir(), appProperties.getMaxUploadBytes());
            } catch (IllegalArgumentException ex) {
                throw new ServiceException(400, ex.getMessage());
            } catch (IOException ex) {
                throw new ServiceException(500, "Failed to store resolution image.", ex);
            }
        }

        try {
            complaint.setStatus(nextStatus);
            if (Complaint.STATUS_RESOLVED.equals(nextStatus) || Complaint.STATUS_CLOSED.equals(nextStatus)) {
                if (complaint.getResolvedAt() == null) {
                    complaint.setResolvedAt(Instant.now());
                }
                if (complaint.getResolvedByUser() == null) {
                    complaint.setResolvedByUser(admin);
                }
                if (!ValidationUtil.isBlank(remarks)) {
                    complaint.setResolutionRemarks(cleanRemarks);
                }
                if (resolutionImgPath != null) {
                    complaint.setResolutionImagePath(resolutionImgPath);
                }
            }
            Complaint saved = complaintRepository.save(complaint);

            ComplaintHistory history = new ComplaintHistory();
            history.setComplaint(saved);
            history.setPreviousStatus(currentStatus);
            history.setNewStatus(nextStatus);
            history.setRemarks(cleanRemarks);
            history.setUpdatedByUser(admin);
            historyRepository.save(history);

            return buildDetailedDto(saved);
        } catch (RuntimeException ex) {
            if (resolutionImgPath != null) {
                FileUploadUtil.deleteQuietly(appProperties.getUploadDir(), resolutionImgPath);
            }
            throw ex;
        }
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getCitizenDashboard(Integer userId) {
        Map<String, Object> dashboard = new LinkedHashMap<>();

        long total = complaintRepository.countByUserUserId(userId);
        long pendingSubmitted = complaintRepository.countByUserUserIdAndStatus(userId, Complaint.STATUS_SUBMITTED);
        long underReview = complaintRepository.countByUserUserIdAndStatus(userId, Complaint.STATUS_UNDER_REVIEW);
        long inProgress = complaintRepository.countByUserUserIdAndStatusIn(
                userId, List.of(Complaint.STATUS_ASSIGNED, Complaint.STATUS_IN_PROGRESS));
        long resolved = complaintRepository.countByUserUserIdAndStatusIn(
                userId, List.of(Complaint.STATUS_RESOLVED, Complaint.STATUS_CLOSED));
        long open = complaintRepository.countByUserUserIdAndStatusIn(userId, List.of(
                Complaint.STATUS_SUBMITTED, Complaint.STATUS_UNDER_REVIEW,
                Complaint.STATUS_ASSIGNED, Complaint.STATUS_IN_PROGRESS));

        dashboard.put("totalComplaints", total);
        dashboard.put("pendingComplaints", pendingSubmitted);
        dashboard.put("underReviewComplaints", underReview);
        dashboard.put("inProgressComplaints", inProgress);
        dashboard.put("resolvedComplaints", resolved);
        dashboard.put("openComplaints", open);
        dashboard.put("possibleRecurringIssues", recurrenceRepository.countDistinctNonRejectedForCitizen(userId));

        Map<String, Long> statusDistribution = new LinkedHashMap<>();
        for (Object[] row : complaintRepository.countStatusDistributionByUser(userId)) {
            statusDistribution.put((String) row[0], (Long) row[1]);
        }
        dashboard.put("statusDistribution", statusDistribution);

        PagedResponse<ComplaintDTO> recent = listCitizenComplaints(userId, null, null, null, 1, 5);
        dashboard.put("recentComplaints", recent.getItems());

        List<RecurrenceDTO> alerts = recurrenceRepository.findDetailedCitizenAlerts(userId).stream()
                .map(RecurrenceDTO::fromEntity)
                .toList();
        dashboard.put("recurrenceAlerts", alerts);

        List<ComplaintHistoryDTO> recentActivity = historyRepository
                .findTop12ByComplaintUserUserIdOrderByUpdatedAtDescHistoryIdDesc(userId).stream()
                .map(ComplaintHistoryDTO::fromEntity)
                .toList();
        dashboard.put("recentActivity", recentActivity);

        return dashboard;
    }

    @Transactional(readOnly = true)
    public boolean isImageAccessibleByUser(String normalizedPath, Integer userId) {
        return complaintRepository.isImageAccessibleByUser(normalizedPath, userId);
    }

    private ComplaintDTO buildDetailedDto(Complaint complaint) {
        List<ComplaintDTO> list = enrichDtos(List.of(complaint));
        ComplaintDTO dto = list.get(0);
        dto.setHistory(historyRepository
                .findByComplaintComplaintIdOrderByUpdatedAtAscHistoryIdAsc(complaint.getComplaintId())
                .stream()
                .map(ComplaintHistoryDTO::fromEntity)
                .toList());
        dto.setRecurrenceRecords(recurrenceRepository
                .findDetailedByComplaintInvolved(complaint.getComplaintId())
                .stream()
                .map(RecurrenceDTO::fromEntity)
                .toList());
        return dto;
    }

    private List<ComplaintDTO> enrichDtos(List<Complaint> complaints) {
        if (complaints == null || complaints.isEmpty()) {
            return List.of();
        }
        List<Integer> ids = complaints.stream()
                .map(Complaint::getComplaintId)
                .filter(Objects::nonNull)
                .toList();

        Map<Integer, Integer> childCounts = new HashMap<>();
        Map<Integer, RecurrenceRecord> topRecurrenceByComplaint = new HashMap<>();

        if (!ids.isEmpty()) {
            for (Object[] row : complaintRepository.countChildrenByParentIds(ids)) {
                childCounts.put((Integer) row[0], ((Long) row[1]).intValue());
            }
            for (RecurrenceRecord rr : recurrenceRepository.findNonRejectedByRecurringIds(ids)) {
                Integer recId = rr.getRecurringComplaint().getComplaintId();
                topRecurrenceByComplaint.putIfAbsent(recId, rr);
            }
        }

        List<ComplaintDTO> dtos = new ArrayList<>(complaints.size());
        for (Complaint c : complaints) {
            ComplaintDTO dto = ComplaintDTO.fromEntity(c);
            dto.setChildRecurrenceCount(childCounts.getOrDefault(c.getComplaintId(), 0));
            RecurrenceRecord rr = topRecurrenceByComplaint.get(c.getComplaintId());
            if (rr != null) {
                dto.setHasRecurrenceAlert(true);
                dto.setRecurrenceScore(rr.getMatchingScore());
                dto.setRecurrenceVerificationStatus(rr.getVerificationStatus());
            } else {
                dto.setHasRecurrenceAlert(false);
            }
            dtos.add(dto);
        }
        return dtos;
    }
}
