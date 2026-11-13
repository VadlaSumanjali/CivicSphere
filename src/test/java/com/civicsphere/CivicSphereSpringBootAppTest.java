package com.civicsphere;

import com.civicsphere.dto.AssignDepartmentRequest;
import com.civicsphere.dto.LoginRequest;
import com.civicsphere.dto.RegisterRequest;
import com.civicsphere.dto.StatusUpdateRequest;
import com.civicsphere.model.Category;
import com.civicsphere.model.Complaint;
import com.civicsphere.model.Department;
import com.civicsphere.model.User;
import com.civicsphere.repository.CategoryRepository;
import com.civicsphere.repository.DepartmentRepository;
import com.civicsphere.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "civicsphere.data-dir=target/test-data-springboot",
        "civicsphere.upload-dir=target/test-uploads-springboot",
        "civicsphere.admin-password=SuperSecretAdmin#2026"
})
class CivicSphereSpringBootAppTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private DepartmentRepository departmentRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void ensureCleanState() {
        User admin = userRepository.findByEmailIgnoreCase("admin@civicsphere.gov.local").orElse(null);
        if (admin == null) {
            admin = new User();
            admin.setFullName("Super Administrator");
            admin.setEmail("admin@civicsphere.gov.local");
            admin.setPhone("9000000001");
            admin.setAddress("Headquarters");
            admin.setPasswordHash(passwordEncoder.encode("SuperSecretAdmin#2026"));
            admin.setRole(User.ROLE_ADMIN);
            userRepository.save(admin);
        } else {
            admin.setPasswordHash(passwordEncoder.encode("SuperSecretAdmin#2026"));
            userRepository.save(admin);
        }
    }

    @Test
    void applicationStartsSuccessfullyWithoutMySQL() {
        List<Category> categories = categoryRepository.findByActiveTrueOrderByNameAsc();
        assertFalse(categories.isEmpty(), "Categories must be initialized upon zero-database startup");

        List<Department> departments = departmentRepository.findByActiveTrueOrderByNameAsc();
        assertFalse(departments.isEmpty(), "Departments must be initialized upon zero-database startup");
    }

    @Test
    void testPublicAndAuthEndpointsWorkflow() throws Exception {
        // 1. Test Public API
        mockMvc.perform(get("/api/public/overview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.categories").isArray())
                .andExpect(jsonPath("$.data.stats.totalComplaints").isNumber());

        mockMvc.perform(get("/api/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray());

        // 2. Test Citizen Registration
        RegisterRequest registerReq = new RegisterRequest();
        registerReq.setFullName("Priya Sharma");
        registerReq.setEmail("priya" + System.currentTimeMillis() + "@example.com");
        registerReq.setPhone("9811223344");
        registerReq.setAddress("Kondapur, Hyderabad");
        registerReq.setPassword("PriyaSecure#2026");
        registerReq.setConfirmPassword("PriyaSecure#2026");

        MvcResult regResult = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.user.role").value(User.ROLE_CITIZEN))
                .andReturn();

        MockHttpSession session = (MockHttpSession) regResult.getRequest().getSession();
        assertNotNull(session, "Session should be established on registration");

        // 3. Test Session Authenticated Endpoint (/api/auth/me)
        mockMvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.user.role").value(User.ROLE_CITIZEN))
                .andExpect(jsonPath("$.data.csrfToken").isNotEmpty());

        // 4. Test Citizen Dashboard Access
        mockMvc.perform(get("/api/complaints/dashboard").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // 5. Test Citizen Cannot Access Admin Dashboard (Role-Based Access Control)
        mockMvc.perform(get("/api/admin/dashboard").session(session))
                .andExpect(status().isForbidden());

        // 6. Test Logout
        mockMvc.perform(post("/api/auth/logout").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // After logout, accessing authenticated endpoint returns 401
        mockMvc.perform(get("/api/complaints/dashboard").session(session))
                .andExpect(status().isUnauthorized());

        // 7. Test Admin Login
        LoginRequest adminLogin = new LoginRequest();
        adminLogin.setEmail("admin@civicsphere.gov.local");
        adminLogin.setPassword("SuperSecretAdmin#2026");

        MvcResult adminLoginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(adminLogin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.user.role").value(User.ROLE_ADMIN))
                .andReturn();

        MockHttpSession adminSession = (MockHttpSession) adminLoginResult.getRequest().getSession();
        assertNotNull(adminSession);

        // Admin can access admin dashboard
        mockMvc.perform(get("/api/admin/dashboard").session(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // Admin can view recurrence alerts
        mockMvc.perform(get("/api/recurrence").session(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
}
