package com.civicsphere.dto;

import com.civicsphere.model.Complaint;
import com.civicsphere.model.RecurrenceRecord;

import java.time.Instant;

public class RecurrenceDTO {
    private Integer recurrenceId;
    private Integer originalComplaintId;
    private Integer recurringComplaintId;
    private int matchingScore;
    private String matchingReason;
    private String verificationStatus;
    private Integer verifiedBy;
    private String verifiedByName;
    private Instant verifiedAt;
    private String verificationRemarks;
    private Instant createdAt;

    private String originalTitle;
    private String originalStatus;
    private Instant originalSubmittedAt;
    private Instant originalResolvedAt;
    private String originalDepartmentName;
    private String recurringTitle;
    private String recurringStatus;
    private Instant recurringSubmittedAt;
    private String categoryName;
    private String locationSummary;
    private String citizenName;

    public static RecurrenceDTO fromEntity(RecurrenceRecord r) {
        RecurrenceDTO dto = new RecurrenceDTO();
        dto.setRecurrenceId(r.getRecurrenceId());
        dto.setMatchingScore(r.getMatchingScore());
        dto.setMatchingReason(r.getMatchingReason());
        dto.setVerificationStatus(r.getVerificationStatus());
        dto.setVerifiedAt(r.getVerifiedAt());
        dto.setVerificationRemarks(r.getVerificationRemarks());
        dto.setCreatedAt(r.getCreatedAt());

        if (r.getVerifiedByUser() != null) {
            dto.setVerifiedBy(r.getVerifiedByUser().getUserId());
            dto.setVerifiedByName(r.getVerifiedByUser().getFullName());
        }

        Complaint orig = r.getOriginalComplaint();
        if (orig != null) {
            dto.setOriginalComplaintId(orig.getComplaintId());
            dto.setOriginalTitle(orig.getTitle());
            dto.setOriginalStatus(orig.getStatus());
            dto.setOriginalSubmittedAt(orig.getSubmittedAt());
            dto.setOriginalResolvedAt(orig.getResolvedAt());
            if (orig.getDepartment() != null) {
                dto.setOriginalDepartmentName(orig.getDepartment().getName());
            }
        }

        Complaint rec = r.getRecurringComplaint();
        if (rec != null) {
            dto.setRecurringComplaintId(rec.getComplaintId());
            dto.setRecurringTitle(rec.getTitle());
            dto.setRecurringStatus(rec.getStatus());
            dto.setRecurringSubmittedAt(rec.getSubmittedAt());
            dto.setLocationSummary(rec.getArea() + ", " + rec.getCity() + ", " + rec.getState());
            if (rec.getCategory() != null) {
                dto.setCategoryName(rec.getCategory().getName());
            }
            if (rec.getUser() != null) {
                dto.setCitizenName(rec.getUser().getFullName());
            }
        }
        return dto;
    }

    public Integer getRecurrenceId() {
        return recurrenceId;
    }

    public void setRecurrenceId(Integer recurrenceId) {
        this.recurrenceId = recurrenceId;
    }

    public Integer getOriginalComplaintId() {
        return originalComplaintId;
    }

    public void setOriginalComplaintId(Integer originalComplaintId) {
        this.originalComplaintId = originalComplaintId;
    }

    public Integer getRecurringComplaintId() {
        return recurringComplaintId;
    }

    public void setRecurringComplaintId(Integer recurringComplaintId) {
        this.recurringComplaintId = recurringComplaintId;
    }

    public int getMatchingScore() {
        return matchingScore;
    }

    public void setMatchingScore(int matchingScore) {
        this.matchingScore = matchingScore;
    }

    public String getMatchingReason() {
        return matchingReason;
    }

    public void setMatchingReason(String matchingReason) {
        this.matchingReason = matchingReason;
    }

    public String getVerificationStatus() {
        return verificationStatus;
    }

    public void setVerificationStatus(String verificationStatus) {
        this.verificationStatus = verificationStatus;
    }

    public Integer getVerifiedBy() {
        return verifiedBy;
    }

    public void setVerifiedBy(Integer verifiedBy) {
        this.verifiedBy = verifiedBy;
    }

    public String getVerifiedByName() {
        return verifiedByName;
    }

    public void setVerifiedByName(String verifiedByName) {
        this.verifiedByName = verifiedByName;
    }

    public Instant getVerifiedAt() {
        return verifiedAt;
    }

    public void setVerifiedAt(Instant verifiedAt) {
        this.verifiedAt = verifiedAt;
    }

    public String getVerificationRemarks() {
        return verificationRemarks;
    }

    public void setVerificationRemarks(String verificationRemarks) {
        this.verificationRemarks = verificationRemarks;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public String getOriginalTitle() {
        return originalTitle;
    }

    public void setOriginalTitle(String originalTitle) {
        this.originalTitle = originalTitle;
    }

    public String getOriginalStatus() {
        return originalStatus;
    }

    public void setOriginalStatus(String originalStatus) {
        this.originalStatus = originalStatus;
    }

    public Instant getOriginalSubmittedAt() {
        return originalSubmittedAt;
    }

    public void setOriginalSubmittedAt(Instant originalSubmittedAt) {
        this.originalSubmittedAt = originalSubmittedAt;
    }

    public Instant getOriginalResolvedAt() {
        return originalResolvedAt;
    }

    public void setOriginalResolvedAt(Instant originalResolvedAt) {
        this.originalResolvedAt = originalResolvedAt;
    }

    public String getOriginalDepartmentName() {
        return originalDepartmentName;
    }

    public void setOriginalDepartmentName(String originalDepartmentName) {
        this.originalDepartmentName = originalDepartmentName;
    }

    public String getRecurringTitle() {
        return recurringTitle;
    }

    public void setRecurringTitle(String recurringTitle) {
        this.recurringTitle = recurringTitle;
    }

    public String getRecurringStatus() {
        return recurringStatus;
    }

    public void setRecurringStatus(String recurringStatus) {
        this.recurringStatus = recurringStatus;
    }

    public Instant getRecurringSubmittedAt() {
        return recurringSubmittedAt;
    }

    public void setRecurringSubmittedAt(Instant recurringSubmittedAt) {
        this.recurringSubmittedAt = recurringSubmittedAt;
    }

    public String getCategoryName() {
        return categoryName;
    }

    public void setCategoryName(String categoryName) {
        this.categoryName = categoryName;
    }

    public String getLocationSummary() {
        return locationSummary;
    }

    public void setLocationSummary(String locationSummary) {
        this.locationSummary = locationSummary;
    }

    public String getCitizenName() {
        return citizenName;
    }

    public void setCitizenName(String citizenName) {
        this.citizenName = citizenName;
    }
}

