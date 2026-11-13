package com.civicsphere.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

public class User {
    public static final String ROLE_CITIZEN = "CITIZEN";
    public static final String ROLE_ADMIN = "ADMIN";

    private Integer userId;
    private String fullName;
    private String email;
    private String phone;

    @JsonProperty("passwordHash")
    private String passwordHash;

    private String address;
    private String role = ROLE_CITIZEN;
    private Instant createdAt;

    public User() {
        this.createdAt = Instant.now();
        this.role = ROLE_CITIZEN;
    }

    public Integer getUserId() {
        return userId;
    }

    public void setUserId(Integer userId) {
        this.userId = userId;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role != null ? role : ROLE_CITIZEN;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
