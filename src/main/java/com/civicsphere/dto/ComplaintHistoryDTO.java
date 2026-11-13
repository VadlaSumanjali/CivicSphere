package com.civicsphere.dto;

import com.civicsphere.model.ComplaintHistory;

import java.time.Instant;

public class ComplaintHistoryDTO {
    private Integer historyId;
    private Integer complaintId;
    private String previousStatus;
    private String newStatus;
    private String remarks;
    private Integer updatedBy;
    private String updatedByName;
    private String updatedByRole;
    private Instant updatedAt;

    public static ComplaintHistoryDTO fromEntity(ComplaintHistory history) {
        ComplaintHistoryDTO dto = new ComplaintHistoryDTO();
        dto.setHistoryId(history.getHistoryId());
        dto.setComplaintId(history.getComplaint() != null ? history.getComplaint().getComplaintId() : null);
        dto.setPreviousStatus(history.getPreviousStatus());
        dto.setNewStatus(history.getNewStatus());
        dto.setRemarks(history.getRemarks());
        if (history.getUpdatedByUser() != null) {
            dto.setUpdatedBy(history.getUpdatedByUser().getUserId());
            dto.setUpdatedByName(history.getUpdatedByUser().getFullName());
            dto.setUpdatedByRole(history.getUpdatedByUser().getRole());
        }
        dto.setUpdatedAt(history.getUpdatedAt());
        return dto;
    }

    public Integer getHistoryId() {
        return historyId;
    }

    public void setHistoryId(Integer historyId) {
        this.historyId = historyId;
    }

    public Integer getComplaintId() {
        return complaintId;
    }

    public void setComplaintId(Integer complaintId) {
        this.complaintId = complaintId;
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

    public Integer getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(Integer updatedBy) {
        this.updatedBy = updatedBy;
    }

    public String getUpdatedByName() {
        return updatedByName;
    }

    public void setUpdatedByName(String updatedByName) {
        this.updatedByName = updatedByName;
    }

    public String getUpdatedByRole() {
        return updatedByRole;
    }

    public void setUpdatedByRole(String updatedByRole) {
        this.updatedByRole = updatedByRole;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}

