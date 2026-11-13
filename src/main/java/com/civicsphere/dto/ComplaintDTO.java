package com.civicsphere.dto;

import com.civicsphere.model.Complaint;

import java.time.Instant;
import java.util.List;

public class ComplaintDTO {
    private Integer complaintId;
    private Integer userId;
    private Integer categoryId;
    private Integer departmentId;
    private Integer parentComplaintId;
    private String title;
    private String description;
    private String area;
    private String city;
    private String state;
    private String landmark;
    private Double latitude;
    private Double longitude;
    private String imagePath;
    private String resolutionImagePath;
    private String status;
    private Instant submittedAt;
    private Instant resolvedAt;
    private String resolutionRemarks;
    private Integer resolvedBy;
    private Instant updatedAt;

    private String citizenName;
    private String citizenEmail;
    private String citizenPhone;
    private String categoryName;
    private String departmentName;
    private String resolvedByName;
    private boolean hasRecurrenceAlert;
    private String recurrenceVerificationStatus;
    private Integer recurrenceScore;
    private int childRecurrenceCount;

    private List<ComplaintHistoryDTO> history;
    private List<RecurrenceDTO> recurrenceRecords;

    public static ComplaintDTO fromEntity(Complaint c) {
        ComplaintDTO dto = new ComplaintDTO();
        dto.setComplaintId(c.getComplaintId());
        if (c.getUser() != null) {
            dto.setUserId(c.getUser().getUserId());
            dto.setCitizenName(c.getUser().getFullName());
            dto.setCitizenEmail(c.getUser().getEmail());
            dto.setCitizenPhone(c.getUser().getPhone());
        }
        if (c.getCategory() != null) {
            dto.setCategoryId(c.getCategory().getCategoryId());
            dto.setCategoryName(c.getCategory().getName());
        }
        if (c.getDepartment() != null) {
            dto.setDepartmentId(c.getDepartment().getDepartmentId());
            dto.setDepartmentName(c.getDepartment().getName());
        }
        if (c.getParentComplaint() != null) {
            dto.setParentComplaintId(c.getParentComplaint().getComplaintId());
        }
        dto.setTitle(c.getTitle());
        dto.setDescription(c.getDescription());
        dto.setArea(c.getArea());
        dto.setCity(c.getCity());
        dto.setState(c.getState());
        dto.setLandmark(c.getLandmark());
        dto.setLatitude(c.getLatitude());
        dto.setLongitude(c.getLongitude());
        dto.setImagePath(c.getImagePath());
        dto.setResolutionImagePath(c.getResolutionImagePath());
        dto.setStatus(c.getStatus());
        dto.setSubmittedAt(c.getSubmittedAt());
        dto.setResolvedAt(c.getResolvedAt());
        dto.setResolutionRemarks(c.getResolutionRemarks());
        if (c.getResolvedByUser() != null) {
            dto.setResolvedBy(c.getResolvedByUser().getUserId());
            dto.setResolvedByName(c.getResolvedByUser().getFullName());
        }
        dto.setUpdatedAt(c.getUpdatedAt());
        return dto;
    }

    public Integer getComplaintId() {
        return complaintId;
    }

    public void setComplaintId(Integer complaintId) {
        this.complaintId = complaintId;
    }

    public Integer getUserId() {
        return userId;
    }

    public void setUserId(Integer userId) {
        this.userId = userId;
    }

    public Integer getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(Integer categoryId) {
        this.categoryId = categoryId;
    }

    public Integer getDepartmentId() {
        return departmentId;
    }

    public void setDepartmentId(Integer departmentId) {
        this.departmentId = departmentId;
    }

    public Integer getParentComplaintId() {
        return parentComplaintId;
    }

    public void setParentComplaintId(Integer parentComplaintId) {
        this.parentComplaintId = parentComplaintId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getArea() {
        return area;
    }

    public void setArea(String area) {
        this.area = area;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public String getLandmark() {
        return landmark;
    }

    public void setLandmark(String landmark) {
        this.landmark = landmark;
    }

    public Double getLatitude() {
        return latitude;
    }

    public void setLatitude(Double latitude) {
        this.latitude = latitude;
    }

    public Double getLongitude() {
        return longitude;
    }

    public void setLongitude(Double longitude) {
        this.longitude = longitude;
    }

    public String getImagePath() {
        return imagePath;
    }

    public void setImagePath(String imagePath) {
        this.imagePath = imagePath;
    }

    public String getResolutionImagePath() {
        return resolutionImagePath;
    }

    public void setResolutionImagePath(String resolutionImagePath) {
        this.resolutionImagePath = resolutionImagePath;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public void setSubmittedAt(Instant submittedAt) {
        this.submittedAt = submittedAt;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }

    public void setResolvedAt(Instant resolvedAt) {
        this.resolvedAt = resolvedAt;
    }

    public String getResolutionRemarks() {
        return resolutionRemarks;
    }

    public void setResolutionRemarks(String resolutionRemarks) {
        this.resolutionRemarks = resolutionRemarks;
    }

    public Integer getResolvedBy() {
        return resolvedBy;
    }

    public void setResolvedBy(Integer resolvedBy) {
        this.resolvedBy = resolvedBy;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getCitizenName() {
        return citizenName;
    }

    public void setCitizenName(String citizenName) {
        this.citizenName = citizenName;
    }

    public String getCitizenEmail() {
        return citizenEmail;
    }

    public void setCitizenEmail(String citizenEmail) {
        this.citizenEmail = citizenEmail;
    }

    public String getCitizenPhone() {
        return citizenPhone;
    }

    public void setCitizenPhone(String citizenPhone) {
        this.citizenPhone = citizenPhone;
    }

    public String getCategoryName() {
        return categoryName;
    }

    public void setCategoryName(String categoryName) {
        this.categoryName = categoryName;
    }

    public String getDepartmentName() {
        return departmentName;
    }

    public void setDepartmentName(String departmentName) {
        this.departmentName = departmentName;
    }

    public String getResolvedByName() {
        return resolvedByName;
    }

    public void setResolvedByName(String resolvedByName) {
        this.resolvedByName = resolvedByName;
    }

    public boolean isHasRecurrenceAlert() {
        return hasRecurrenceAlert;
    }

    public void setHasRecurrenceAlert(boolean hasRecurrenceAlert) {
        this.hasRecurrenceAlert = hasRecurrenceAlert;
    }

    public String getRecurrenceVerificationStatus() {
        return recurrenceVerificationStatus;
    }

    public void setRecurrenceVerificationStatus(String recurrenceVerificationStatus) {
        this.recurrenceVerificationStatus = recurrenceVerificationStatus;
    }

    public Integer getRecurrenceScore() {
        return recurrenceScore;
    }

    public void setRecurrenceScore(Integer recurrenceScore) {
        this.recurrenceScore = recurrenceScore;
    }

    public int getChildRecurrenceCount() {
        return childRecurrenceCount;
    }

    public void setChildRecurrenceCount(int childRecurrenceCount) {
        this.childRecurrenceCount = childRecurrenceCount;
    }

    public List<ComplaintHistoryDTO> getHistory() {
        return history;
    }

    public void setHistory(List<ComplaintHistoryDTO> history) {
        this.history = history;
    }

    public List<RecurrenceDTO> getRecurrenceRecords() {
        return recurrenceRecords;
    }

    public void setRecurrenceRecords(List<RecurrenceDTO> recurrenceRecords) {
        this.recurrenceRecords = recurrenceRecords;
    }
}

