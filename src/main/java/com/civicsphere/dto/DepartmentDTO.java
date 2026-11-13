package com.civicsphere.dto;

import com.civicsphere.model.Department;

import java.time.Instant;

public class DepartmentDTO {
    private Integer departmentId;
    private String name;
    private String description;
    private String contactEmail;
    private String contactPhone;
    private boolean active;
    private Instant createdAt;
    private Instant updatedAt;
    private long assignedComplaintsCount;

    public static DepartmentDTO fromEntity(Department dept, long assignedCount) {
        DepartmentDTO dto = new DepartmentDTO();
        dto.setDepartmentId(dept.getDepartmentId());
        dto.setName(dept.getName());
        dto.setDescription(dept.getDescription());
        dto.setContactEmail(dept.getContactEmail());
        dto.setContactPhone(dept.getContactPhone());
        dto.setActive(dept.isActive());
        dto.setCreatedAt(dept.getCreatedAt());
        dto.setUpdatedAt(dept.getUpdatedAt());
        dto.setAssignedComplaintsCount(assignedCount);
        return dto;
    }

    public Integer getDepartmentId() {
        return departmentId;
    }

    public void setDepartmentId(Integer departmentId) {
        this.departmentId = departmentId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getContactEmail() {
        return contactEmail;
    }

    public void setContactEmail(String contactEmail) {
        this.contactEmail = contactEmail;
    }

    public String getContactPhone() {
        return contactPhone;
    }

    public void setContactPhone(String contactPhone) {
        this.contactPhone = contactPhone;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public long getAssignedComplaintsCount() {
        return assignedComplaintsCount;
    }

    public void setAssignedComplaintsCount(long assignedComplaintsCount) {
        this.assignedComplaintsCount = assignedComplaintsCount;
    }
}

