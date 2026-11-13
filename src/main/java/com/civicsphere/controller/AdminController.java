package com.civicsphere.controller;

import com.civicsphere.dto.ApiResponse;
import com.civicsphere.dto.AssignDepartmentRequest;
import com.civicsphere.dto.ComplaintDTO;
import com.civicsphere.dto.PagedResponse;
import com.civicsphere.security.UserPrincipal;
import com.civicsphere.service.AdminService;
import com.civicsphere.service.ComplaintService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final AdminService adminService;
    private final ComplaintService complaintService;

    public AdminController(AdminService adminService, ComplaintService complaintService) {
        this.adminService = adminService;
        this.complaintService = complaintService;
    }

    @GetMapping("/dashboard")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getDashboard() {
        return ResponseEntity.ok(ApiResponse.ok("Admin dashboard analytics loaded.",
                adminService.getDashboardAnalytics()));
    }

    @GetMapping("/complaints")
    public ResponseEntity<ApiResponse<PagedResponse<ComplaintDTO>>> listComplaints(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Integer categoryId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String location,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "12") int pageSize) {
        PagedResponse<ComplaintDTO> paged = complaintService.listAllComplaints(
                search, categoryId, status, location, page, pageSize);
        return ResponseEntity.ok(ApiResponse.ok("Admin complaints loaded.", paged));
    }

    @PutMapping("/complaints/{id}/assign")
    public ResponseEntity<ApiResponse<ComplaintDTO>> assignDepartment(
            @PathVariable Integer id,
            @Valid @RequestBody AssignDepartmentRequest body,
            @AuthenticationPrincipal UserPrincipal principal) {
        ComplaintDTO updated = complaintService.assignDepartment(
                id, body.getDepartmentId(), body.getRemarks(), principal.getUserId());
        return ResponseEntity.ok(ApiResponse.ok(
                "Department assigned to Complaint #" + id + ".", updated));
    }
}

