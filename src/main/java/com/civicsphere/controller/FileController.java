package com.civicsphere.controller;

import com.civicsphere.config.AppProperties;
import com.civicsphere.dto.ApiResponse;
import com.civicsphere.security.UserPrincipal;
import com.civicsphere.service.ComplaintService;
import com.civicsphere.util.FileUploadUtil;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Path;

@RestController
@RequestMapping("/api/files")
public class FileController {

    private final ComplaintService complaintService;
    private final AppProperties appProperties;

    public FileController(ComplaintService complaintService, AppProperties appProperties) {
        this.complaintService = complaintService;
        this.appProperties = appProperties;
    }

    @GetMapping("/{folder}/{filename}")
    public ResponseEntity<?> serveFile(
            @PathVariable String folder,
            @PathVariable String filename,
            @AuthenticationPrincipal UserPrincipal principal) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Authentication required."));
        }

        String relativeKey = folder + "/" + filename;
        Path safePath = FileUploadUtil.resolveSafeUploadPath(appProperties.getUploadDir(), relativeKey);
        if (safePath == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("File not found."));
        }

        if (!principal.isAdmin() && !complaintService.isImageAccessibleByUser(relativeKey, principal.getUserId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("You are not authorized to access this evidence file."));
        }

        MediaType mediaType = MediaType.parseMediaType(
                FileUploadUtil.detectContentType(safePath.getFileName().toString()));
        Resource resource = new FileSystemResource(safePath);
        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=3600")
                .body(resource);
    }
}

