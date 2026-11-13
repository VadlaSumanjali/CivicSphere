package com.civicsphere.controller;

import com.civicsphere.dto.ApiResponse;
import com.civicsphere.dto.LoginRequest;
import com.civicsphere.dto.RegisterRequest;
import com.civicsphere.dto.UserDTO;
import com.civicsphere.model.User;
import com.civicsphere.security.UserPrincipal;
import com.civicsphere.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final SecurityContextRepository securityContextRepository;
    private final HttpSessionCsrfTokenRepository csrfTokenRepository;

    public AuthController(AuthService authService,
                          SecurityContextRepository securityContextRepository,
                          HttpSessionCsrfTokenRepository csrfTokenRepository) {
        this.authService = authService;
        this.securityContextRepository = securityContextRepository;
        this.csrfTokenRepository = csrfTokenRepository;
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<Map<String, Object>>> registerCitizen(
            @Valid @RequestBody RegisterRequest reqBody,
            HttpServletRequest request,
            HttpServletResponse response) {
        User created = authService.registerCitizen(
                reqBody.getFullName(),
                reqBody.getEmail(),
                reqBody.getPhone(),
                reqBody.getAddress(),
                reqBody.getPassword(),
                reqBody.getConfirmPassword()
        );
        String csrf = saveAuthenticatedSession(request, response, created);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Registration successful.", sessionPayload(created, csrf)));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<Map<String, Object>>> loginUser(
            @Valid @RequestBody LoginRequest reqBody,
            HttpServletRequest request,
            HttpServletResponse response) {
        User verified = authService.authenticate(reqBody.getEmail(), reqBody.getPassword());
        String csrf = saveAuthenticatedSession(request, response, verified);
        return ResponseEntity.ok(ApiResponse.ok("Signed in successfully.", sessionPayload(verified, csrf)));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logoutUser(HttpServletRequest request) {
        HttpSession existingSession = request.getSession(false);
        if (existingSession != null) {
            existingSession.invalidate();
        }
        SecurityContextHolder.clearContext();
        return ResponseEntity.ok(ApiResponse.ok("Signed out successfully.", null));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<Map<String, Object>>> currentUser(
            @AuthenticationPrincipal UserPrincipal principal,
            HttpServletRequest request,
            HttpServletResponse response) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Authentication required. Please sign in."));
        }
        CsrfToken token = csrfTokenRepository.loadToken(request);
        if (token == null) {
            token = csrfTokenRepository.generateToken(request);
            csrfTokenRepository.saveToken(token, request, response);
        }
        User fresh = authService.getById(principal.getUserId());
        return ResponseEntity.ok(ApiResponse.ok("Authenticated session active.",
                sessionPayload(fresh, token.getToken())));
    }

    private String saveAuthenticatedSession(HttpServletRequest request, HttpServletResponse response, User user) {
        request.getSession(true);
        request.changeSessionId();

        UserPrincipal principal = new UserPrincipal(user);
        UsernamePasswordAuthenticationToken token =
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(token);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);

        CsrfToken csrfToken = csrfTokenRepository.generateToken(request);
        csrfTokenRepository.saveToken(csrfToken, request, response);
        return csrfToken.getToken();
    }

    private Map<String, Object> sessionPayload(User user, String csrfToken) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("user", UserDTO.fromEntity(user));
        result.put("csrfToken", csrfToken);
        return result;
    }
}

