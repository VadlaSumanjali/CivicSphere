package com.civicsphere.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

public class Complaint {
    public static final String STATUS_SUBMITTED = "SUBMITTED";
    public static final String STATUS_UNDER_REVIEW = "UNDER_REVIEW";
    public static final String STATUS_ASSIGNED = "ASSIGNED";
    public static final String STATUS_IN_PROGRESS = "IN_PROGRESS";
    public static final String STATUS_RESOLVED = "RESOLVED";
    public static final String STATUS_CLOSED = "CLOSED";

    private Integer complaintId;

    @JsonProperty("userId")
    private Integer userId;

    @JsonProperty("categoryId")
    private Integer categoryId;

    @JsonProperty("departmentId")
    private Integer departmentId;

    @JsonProperty("parentComplaintId")
    private Integer parentComplaintId;

    @JsonProperty("resolvedByUserId")
    private Integer resolvedByUserId;

    @JsonIgnore
    private User user;

    @JsonIgnore
    private Category category;

    @JsonIgnore
    private Department department;

    @JsonIgnore
    private Complaint parentComplaint;

    @JsonIgnore
    private User resolvedByUser;

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
    private String status = STATUS_SUBMITTED;
    private Instant submittedAt;
    private Instant resolvedAt;
    private String resolutionRemarks;
    private Instant updatedAt;

    public Complaint() {
        Instant now = Instant.now();
        this.submittedAt = now;
        this.updatedAt = now;
        this.status = STATUS_SUBMITTED;
    }

    public Integer getComplaintId() {
        return complaintId;
    }

    public void setComplaintId(Integer complaintId) {
        this.complaintId = complaintId;
    }

    public Integer getUserId() {
        return userId != null ? userId : (user != null ? user.getUserId() : null);
    }

    public void setUserId(Integer userId) {
        this.userId = userId;
    }

    public Integer getCategoryId() {
        return categoryId != null ? categoryId : (category != null ? category.getCategoryId() : null);
    }

    public void setCategoryId(Integer categoryId) {
        this.categoryId = categoryId;
    }

    public Integer getDepartmentId() {
        return departmentId != null ? departmentId : (department != null ? department.getDepartmentId() : null);
    }

    public void setDepartmentId(Integer departmentId) {
        this.departmentId = departmentId;
    }

    public Integer getParentComplaintId() {
        return parentComplaintId != null ? parentComplaintId : (parentComplaint != null ? parentComplaint.getComplaintId() : null);
    }

    public void setParentComplaintId(Integer parentComplaintId) {
        this.parentComplaintId = parentComplaintId;
    }

    public Integer getResolvedByUserId() {
        return resolvedByUserId != null ? resolvedByUserId : (resolvedByUser != null ? resolvedByUser.getUserId() : null);
    }

    public void setResolvedByUserId(Integer resolvedByUserId) {
        this.resolvedByUserId = resolvedByUserId;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
        if (user != null) {
            this.userId = user.getUserId();
        }
    }

    public Category getCategory() {
        return category;
    }

    public void setCategory(Category category) {
        this.category = category;
        if (category != null) {
            this.categoryId = category.getCategoryId();
        }
    }

    public Department getDepartment() {
        return department;
    }

    public void setDepartment(Department department) {
        this.department = department;
        if (department != null) {
            this.departmentId = department.getDepartmentId();
        }
    }

    public Complaint getParentComplaint() {
        return parentComplaint;
    }

    public void setParentComplaint(Complaint parentComplaint) {
        this.parentComplaint = parentComplaint;
        if (parentComplaint != null) {
            this.parentComplaintId = parentComplaint.getComplaintId();
        } else {
            this.parentComplaintId = null;
        }
    }

    public User getResolvedByUser() {
        return resolvedByUser;
    }

    public void setResolvedByUser(User resolvedByUser) {
        this.resolvedByUser = resolvedByUser;
        if (resolvedByUser != null) {
            this.resolvedByUserId = resolvedByUser.getUserId();
        } else {
            this.resolvedByUserId = null;
        }
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
        this.status = status != null ? status : STATUS_SUBMITTED;
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

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
