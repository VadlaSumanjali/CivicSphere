package com.civicsphere.controller;

import com.civicsphere.dto.ApiResponse;
import com.civicsphere.model.Category;
import com.civicsphere.service.ComplaintService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
public class PublicController {

    private final ComplaintService complaintService;

    public PublicController(ComplaintService complaintService) {
        this.complaintService = complaintService;
    }

    @GetMapping("/api/categories")
    public ResponseEntity<ApiResponse<List<Category>>> getCategories() {
        List<Category> items = complaintService.listActiveCategories();
        return ResponseEntity.ok(ApiResponse.ok("Categories loaded.", items));
    }

    @GetMapping("/api/public/overview")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getPublicOverview() {
        Map<String, Object> overview = complaintService.getPublicOverview();
        return ResponseEntity.ok(ApiResponse.ok("Platform overview loaded.", overview));
    }
}

