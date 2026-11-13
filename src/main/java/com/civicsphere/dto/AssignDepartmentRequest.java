package com.civicsphere.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public class AssignDepartmentRequest {

    @NotNull(message = "Valid departmentId is required.")
    @Positive(message = "Valid departmentId is required.")
    private Integer departmentId;

    private String remarks;

    public Integer getDepartmentId() {
        return departmentId;
    }

    public void setDepartmentId(Integer departmentId) {
        this.departmentId = departmentId;
    }

    public String getRemarks() {
        return remarks;
    }

    public void setRemarks(String remarks) {
        this.remarks = remarks;
    }
}

