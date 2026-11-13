package com.civicsphere.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

public class ComplaintHistory {

    private Integer historyId;

    @JsonProperty("complaintId")
    private Integer complaintId;

    private String previousStatus;
    private String newStatus;
    private String remarks;

    @JsonProperty("updatedByUserId")
    private Integer updatedByUserId;

    private Instant updatedAt;

    @JsonIgnore
    private Complaint complaint;

    @JsonIgnore
    private User updatedByUser;

    public ComplaintHistory() {
        this.updatedAt = Instant.now();
    }

    public Integer getHistoryId() {
        return historyId;
    }

    public void setHistoryId(Integer historyId) {
        this.historyId = historyId;
    }

    public Integer getComplaintId() {
        return complaintId != null ? complaintId : (complaint != null ? complaint.getComplaintId() : null);
    }

    public void setComplaintId(Integer complaintId) {
        this.complaintId = complaintId;
    }

    public Integer getUpdatedByUserId() {
        return updatedByUserId != null ? updatedByUserId : (updatedByUser != null ? updatedByUser.getUserId() : null);
    }

    public void setUpdatedByUserId(Integer updatedByUserId) {
        this.updatedByUserId = updatedByUserId;
    }

    public Complaint getComplaint() {
        return complaint;
    }

    public void setComplaint(Complaint complaint) {
        this.complaint = complaint;
        if (complaint != null) {
            this.complaintId = complaint.getComplaintId();
        }
    }

    public String getPreviousStatus() {
        return previousStatus;
    }

    public void setPreviousStatus(String previousStatus) {
        this.previousStatus = previousStatus;
    }

    public String getNewStatus() {
        return newStatus;
    }

    public void setNewStatus(String newStatus) {
        this.newStatus = newStatus;
    }

    public String getRemarks() {
        return remarks;
    }

    public void setRemarks(String remarks) {
        this.remarks = remarks;
    }

    public User getUpdatedByUser() {
        return updatedByUser;
    }

    public void setUpdatedByUser(User updatedByUser) {
        this.updatedByUser = updatedByUser;
        if (updatedByUser != null) {
            this.updatedByUserId = updatedByUser.getUserId();
        }
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
