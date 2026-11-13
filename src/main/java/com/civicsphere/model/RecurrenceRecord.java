package com.civicsphere.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

public class RecurrenceRecord {
    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_CONFIRMED = "CONFIRMED";
    public static final String STATUS_REJECTED = "REJECTED";

    private Integer recurrenceId;

    @JsonProperty("originalComplaintId")
    private Integer originalComplaintId;

    @JsonProperty("recurringComplaintId")
    private Integer recurringComplaintId;

    private int matchingScore;
    private String matchingReason;
    private String verificationStatus = STATUS_PENDING;

    @JsonProperty("verifiedByUserId")
    private Integer verifiedByUserId;

    private Instant verifiedAt;
    private String verificationRemarks;
    private Instant createdAt;

    @JsonIgnore
    private Complaint originalComplaint;

    @JsonIgnore
    private Complaint recurringComplaint;

    @JsonIgnore
    private User verifiedByUser;

    public RecurrenceRecord() {
        this.createdAt = Instant.now();
        this.verificationStatus = STATUS_PENDING;
    }

    public Integer getRecurrenceId() {
        return recurrenceId;
    }

    public void setRecurrenceId(Integer recurrenceId) {
        this.recurrenceId = recurrenceId;
    }

    public Integer getOriginalComplaintId() {
        return originalComplaintId != null ? originalComplaintId : (originalComplaint != null ? originalComplaint.getComplaintId() : null);
    }

    public void setOriginalComplaintId(Integer originalComplaintId) {
        this.originalComplaintId = originalComplaintId;
    }

    public Integer getRecurringComplaintId() {
        return recurringComplaintId != null ? recurringComplaintId : (recurringComplaint != null ? recurringComplaint.getComplaintId() : null);
    }

    public void setRecurringComplaintId(Integer recurringComplaintId) {
        this.recurringComplaintId = recurringComplaintId;
    }

    public Integer getVerifiedByUserId() {
        return verifiedByUserId != null ? verifiedByUserId : (verifiedByUser != null ? verifiedByUser.getUserId() : null);
    }

    public void setVerifiedByUserId(Integer verifiedByUserId) {
        this.verifiedByUserId = verifiedByUserId;
    }

    public Complaint getOriginalComplaint() {
        return originalComplaint;
    }

    public void setOriginalComplaint(Complaint originalComplaint) {
        this.originalComplaint = originalComplaint;
        if (originalComplaint != null) {
            this.originalComplaintId = originalComplaint.getComplaintId();
        }
    }

    public Complaint getRecurringComplaint() {
        return recurringComplaint;
    }

    public void setRecurringComplaint(Complaint recurringComplaint) {
        this.recurringComplaint = recurringComplaint;
        if (recurringComplaint != null) {
            this.recurringComplaintId = recurringComplaint.getComplaintId();
        }
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
        this.verificationStatus = verificationStatus != null ? verificationStatus : STATUS_PENDING;
    }

    public User getVerifiedByUser() {
        return verifiedByUser;
    }

    public void setVerifiedByUser(User verifiedByUser) {
        this.verifiedByUser = verifiedByUser;
        if (verifiedByUser != null) {
            this.verifiedByUserId = verifiedByUser.getUserId();
        } else {
            this.verifiedByUserId = null;
        }
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
}
