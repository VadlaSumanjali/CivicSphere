package com.civicsphere.controller;

import com.civicsphere.dto.ApiResponse;
import com.civicsphere.dto.DepartmentDTO;
import com.civicsphere.dto.DepartmentRequest;
import com.civicsphere.service.DepartmentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/departments")
public class DepartmentController {

    private final DepartmentService departmentService;

    public DepartmentController(DepartmentService departmentService) {
        this.departmentService = departmentService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<DepartmentDTO>>> list(
            @RequestParam(defaultValue = "false") boolean activeOnly) {
        return ResponseEntity.ok(ApiResponse.ok("Departments loaded.",
                departmentService.listDepartments(activeOnly)));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<DepartmentDTO>> create(@Valid @RequestBody DepartmentRequest body) {
        DepartmentDTO created = departmentService.createDepartment(
                body.getName(), body.getDescription(), body.getContactEmail(), body.getContactPhone());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Department created.", created));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<DepartmentDTO>> update(
            @PathVariable Integer id,
            @Valid @RequestBody DepartmentRequest body) {
        DepartmentDTO updated = departmentService.updateDepartment(
                id, body.getName(), body.getDescription(),
                body.getContactEmail(), body.getContactPhone(), body.getActive());
        return ResponseEntity.ok(ApiResponse.ok("Department updated.", updated));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<DepartmentDTO>> deactivate(@PathVariable Integer id) {
        DepartmentDTO deactivated = departmentService.deactivateDepartment(id);
        return ResponseEntity.ok(ApiResponse.ok("Department deactivated.", deactivated));
    }
}

