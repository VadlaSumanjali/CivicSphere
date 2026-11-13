package com.civicsphere.controller;

import com.civicsphere.dto.ApiResponse;
import com.civicsphere.dto.ComplaintDTO;
import com.civicsphere.dto.ComplaintRequest;
import com.civicsphere.dto.PagedResponse;
import com.civicsphere.dto.StatusUpdateRequest;
import com.civicsphere.security.UserPrincipal;
import com.civicsphere.service.ComplaintService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/complaints")
public class ComplaintController {

    private final ComplaintService complaintService;

    public ComplaintController(ComplaintService complaintService) {
        this.complaintService = complaintService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PagedResponse<ComplaintDTO>>> listComplaints(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Integer categoryId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String location,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        PagedResponse<ComplaintDTO> result = principal.isAdmin()
                ? complaintService.listAllComplaints(search, categoryId, status, location, page, pageSize)
                : complaintService.listCitizenComplaints(principal.getUserId(), search, categoryId, status, page, pageSize);
        return ResponseEntity.ok(ApiResponse.ok("Complaints loaded.", result));
    }

    @GetMapping("/my")
    public ResponseEntity<ApiResponse<PagedResponse<ComplaintDTO>>> listMyComplaints(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Integer categoryId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        PagedResponse<ComplaintDTO> result = complaintService.listCitizenComplaints(
                principal.getUserId(), search, categoryId, status, page, pageSize);
        return ResponseEntity.ok(ApiResponse.ok("Complaints loaded.", result));
    }

    @GetMapping("/dashboard")
    public ResponseEntity<ApiResponse<Map<String, Object>>> citizenDashboard(
            @AuthenticationPrincipal UserPrincipal principal) {
        Map<String, Object> dashboard = complaintService.getCitizenDashboard(principal.getUserId());
        return ResponseEntity.ok(ApiResponse.ok("Citizen dashboard loaded.", dashboard));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ComplaintDTO>> details(
            @PathVariable Integer id,
            @AuthenticationPrincipal UserPrincipal principal) {
        ComplaintDTO detail = complaintService.getComplaintDetails(id, principal.getUser());
        return ResponseEntity.ok(ApiResponse.ok("Complaint details loaded.", detail));
    }

    @GetMapping("/{id}/recurrences")
    public ResponseEntity<ApiResponse<List<ComplaintDTO>>> relatedRecurrences(
            @PathVariable Integer id,
            @AuthenticationPrincipal UserPrincipal principal) {
        List<ComplaintDTO> related = complaintService.getRelatedRecurrences(id, principal.getUser());
        return ResponseEntity.ok(ApiResponse.ok("Related complaints loaded.", related));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<ComplaintDTO>> submitMultipart(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam Integer categoryId,
            @RequestParam String title,
            @RequestParam String description,
            @RequestParam String area,
            @RequestParam String city,
            @RequestParam String state,
            @RequestParam(required = false) String landmark,
            @RequestParam(required = false) Double latitude,
            @RequestParam(required = false) Double longitude,
            @RequestParam(required = false) Integer parentComplaintId,
            @RequestParam(value = "image", required = false) MultipartFile image) {
        ComplaintDTO created = complaintService.submitComplaint(
                principal.getUserId(), categoryId, title, description,
                area, city, state, landmark, latitude, longitude, image, parentComplaintId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Complaint submitted successfully.", created));
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<ComplaintDTO>> submitJson(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody ComplaintRequest body) {
        ComplaintDTO created = complaintService.submitComplaint(
                principal.getUserId(), body.getCategoryId(), body.getTitle(), body.getDescription(),
                body.getArea(), body.getCity(), body.getState(), body.getLandmark(),
                body.getLatitude(), body.getLongitude(), null, body.getParentComplaintId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Complaint submitted successfully.", created));
    }

    @PostMapping(value = "/{id}/recurrence", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<ComplaintDTO>> reportRecurrenceMultipart(
            @PathVariable Integer id,
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam Integer categoryId,
            @RequestParam String title,
            @RequestParam String description,
            @RequestParam String area,
            @RequestParam String city,
            @RequestParam String state,
            @RequestParam(required = false) String landmark,
            @RequestParam(required = false) Double latitude,
            @RequestParam(required = false) Double longitude,
            @RequestParam(value = "image", required = false) MultipartFile image) {
        ComplaintDTO created = complaintService.submitComplaint(
                principal.getUserId(), categoryId, title, description,
                area, city, state, landmark, latitude, longitude, image, id);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Recurrence report submitted and linked to Complaint #" + id + ".", created));
    }

    @PostMapping(value = "/{id}/recurrence", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<ComplaintDTO>> reportRecurrenceJson(
            @PathVariable Integer id,
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody ComplaintRequest body) {
        ComplaintDTO created = complaintService.submitComplaint(
                principal.getUserId(), body.getCategoryId(), body.getTitle(), body.getDescription(),
                body.getArea(), body.getCity(), body.getState(), body.getLandmark(),
                body.getLatitude(), body.getLongitude(), null, id);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Recurrence report submitted and linked to Complaint #" + id + ".", created));
    }

    @RequestMapping(value = "/{id}/status",
            method = {RequestMethod.POST, RequestMethod.PUT},
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<ComplaintDTO>> updateStatusMultipart(
            @PathVariable Integer id,
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam String status,
            @RequestParam(required = false) String remarks,
            @RequestParam(value = "resolutionImage", required = false) MultipartFile resolutionImage) {
        ComplaintDTO updated = complaintService.updateStatus(
                id, status, remarks, resolutionImage, principal.getUserId());
        return ResponseEntity.ok(ApiResponse.ok("Complaint status updated.", updated));
    }

    @RequestMapping(value = "/{id}/status",
            method = {RequestMethod.POST, RequestMethod.PUT},
            consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<ComplaintDTO>> updateStatusJson(
            @PathVariable Integer id,
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody StatusUpdateRequest body) {
        ComplaintDTO updated = complaintService.updateStatus(
                id, body.getStatus(), body.getRemarks(), null, principal.getUserId());
        return ResponseEntity.ok(ApiResponse.ok("Complaint status updated.", updated));
    }
}

