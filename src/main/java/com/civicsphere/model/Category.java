package com.civicsphere.model;

import java.time.Instant;

public class Category {

    private Integer categoryId;
    private String name;
    private String description;
    private String iconName = "alert-circle";
    private boolean active = true;
    private Instant createdAt;

    public Category() {
        this.createdAt = Instant.now();
        this.iconName = "alert-circle";
        this.active = true;
    }

    public Category(Integer categoryId, String name, String description, String iconName, boolean active) {
        this.categoryId = categoryId;
        this.name = name;
        this.description = description;
        this.iconName = iconName != null ? iconName : "alert-circle";
        this.active = active;
        this.createdAt = Instant.now();
    }

    public Integer getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(Integer categoryId) {
        this.categoryId = categoryId;
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

    public String getIconName() {
        return iconName;
    }

    public void setIconName(String iconName) {
        this.iconName = iconName != null ? iconName : "alert-circle";
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
}
