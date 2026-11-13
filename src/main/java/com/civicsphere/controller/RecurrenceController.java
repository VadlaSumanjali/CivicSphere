package com.civicsphere.controller;

import com.civicsphere.dto.ApiResponse;
import com.civicsphere.dto.RecurrenceDTO;
import com.civicsphere.dto.VerifyRecurrenceRequest;
import com.civicsphere.security.UserPrincipal;
import com.civicsphere.service.RecurrenceService;
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

import java.util.List;

@RestController
@RequestMapping("/api/recurrence")
@PreAuthorize("hasRole('ADMIN')")
public class RecurrenceController {

    private final RecurrenceService recurrenceService;

    public RecurrenceController(RecurrenceService recurrenceService) {
        this.recurrenceService = recurrenceService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<RecurrenceDTO>>> list(
            @RequestParam(required = false) String status) {
        return ResponseEntity.ok(ApiResponse.ok("Recurrence records loaded.",
                recurrenceService.listRecurrences(status)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<RecurrenceDTO>> details(@PathVariable Integer id) {
        return ResponseEntity.ok(ApiResponse.ok("Recurrence details loaded.",
                recurrenceService.getRecurrenceById(id)));
    }

    @PutMapping("/{id}/verify")
    public ResponseEntity<ApiResponse<RecurrenceDTO>> verify(
            @PathVariable Integer id,
            @Valid @RequestBody VerifyRecurrenceRequest body,
            @AuthenticationPrincipal UserPrincipal principal) {
        RecurrenceDTO verified = recurrenceService.verifyRecurrence(
                id, body.getStatus(), body.getRemarks(), principal.getUserId());
        return ResponseEntity.ok(ApiResponse.ok(
                "Recurrence #" + id + " marked as " + verified.getVerificationStatus() + ".", verified));
    }
}

