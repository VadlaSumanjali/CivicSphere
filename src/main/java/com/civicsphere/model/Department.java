package com.civicsphere.model;

import java.time.Instant;

public class Department {

    private Integer departmentId;
    private String name;
    private String description;
    private String contactEmail;
    private String contactPhone;
    private boolean active = true;
    private Instant createdAt;
    private Instant updatedAt;

    public Department() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
        this.active = true;
    }

    public Department(Integer departmentId, String name, String description,
                      String contactEmail, String contactPhone, boolean active) {
        Instant now = Instant.now();
        this.departmentId = departmentId;
        this.name = name;
        this.description = description;
        this.contactEmail = contactEmail;
        this.contactPhone = contactPhone;
        this.active = active;
        this.createdAt = now;
        this.updatedAt = now;
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
}
