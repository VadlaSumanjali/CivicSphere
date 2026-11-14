package com.civicsphere.config;

import com.civicsphere.dto.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final ObjectMapper objectMapper;

    public SecurityConfig(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    public HttpSessionCsrfTokenRepository csrfTokenRepository() {
        HttpSessionCsrfTokenRepository repository = new HttpSessionCsrfTokenRepository();
        repository.setHeaderName("X-CSRF-Token");
        return repository;
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(List.of("*"));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setExposedHeaders(List.of("X-CSRF-Token", "Set-Cookie"));
        configuration.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   SecurityContextRepository securityContextRepository,
                                                   HttpSessionCsrfTokenRepository csrfTokenRepository) throws Exception {
        CsrfTokenRequestAttributeHandler requestHandler = new CsrfTokenRequestAttributeHandler();
        // Force immediate evaluation of deferred CsrfToken so AuthController returns active token
        requestHandler.setCsrfRequestAttributeName(null);

        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .securityContext(context -> context.securityContextRepository(securityContextRepository))
                .sessionManagement(session -> session.sessionFixation(fix -> fix.changeSessionId()))
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfTokenRepository)
                        .csrfTokenRequestHandler(requestHandler)
                        .ignoringRequestMatchers(
                                "/api/auth/login", "/api/auth/citizen/login", "/api/auth/admin/login",
                                "/api/auth/register", "/api/auth/logout"
                        )
                )
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(
                                "/", "/index.html", "/login", "/login.html", "/register", "/register.html",
                                "/citizen/login", "/citizen/login.html", "/citizen/register",
                                "/admin/login", "/admin/login.html",
                                "/css/**", "/js/**", "/assets/**", "/favicon.ico", "/error"
                        ).permitAll()
                        .requestMatchers(
                                "/api/public/**", "/api/categories",
                                "/api/auth/login", "/api/auth/citizen/login", "/api/auth/admin/login",
                                "/api/auth/register", "/api/auth/logout"
                        ).permitAll()
                        .requestMatchers("/citizen/**", "/citizen/dashboard").hasRole("CITIZEN")
                        .requestMatchers("/admin/**", "/admin/dashboard", "/api/admin/**", "/api/recurrence", "/api/recurrence/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/departments", "/api/complaints/*/status").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/departments/**", "/api/complaints/*/status").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/departments/**").hasRole("ADMIN")
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().authenticated()
                )
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((req, resp, authEx) -> {
                            if (isApiRequest(req)) {
                                writeJsonError(resp, HttpServletResponse.SC_UNAUTHORIZED,
                                        "Authentication required. Please sign in.");
                            } else {
                                String uri = req.getRequestURI();
                                if (uri != null && uri.contains("/admin/")) {
                                    resp.sendRedirect(req.getContextPath() + "/admin/login.html");
                                } else {
                                    resp.sendRedirect(req.getContextPath() + "/citizen/login.html");
                                }
                            }
                        })
                        .accessDeniedHandler((req, resp, deniedEx) -> {
                            if (isApiRequest(req)) {
                                String message = deniedEx instanceof CsrfException
                                        ? "Invalid or missing CSRF security token. Please refresh and try again."
                                        : "Access denied. Insufficient privileges.";
                                writeJsonError(resp, HttpServletResponse.SC_FORBIDDEN, message);
                            } else {
                                String uri = req.getRequestURI();
                                if (uri != null && uri.contains("/admin/")) {
                                    resp.sendRedirect(req.getContextPath() + "/citizen/dashboard.html?error=admin_required");
                                } else if (uri != null && uri.contains("/citizen/")) {
                                    resp.sendRedirect(req.getContextPath() + "/admin/dashboard.html?error=citizen_only");
                                } else {
                                    resp.sendRedirect(req.getContextPath() + "/login.html");
                                }
                            }
                        })
                )
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable());

        return http.build();
    }

    private boolean isApiRequest(HttpServletRequest req) {
        String uri = req.getRequestURI();
        return uri != null && uri.startsWith(req.getContextPath() + "/api/");
    }

    private void writeJsonError(HttpServletResponse resp, int status, String message) throws IOException {
        resp.setStatus(status);
        resp.setContentType(MediaType.APPLICATION_JSON_VALUE);
        resp.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(resp.getWriter(), ApiResponse.error(message));
    }
}

