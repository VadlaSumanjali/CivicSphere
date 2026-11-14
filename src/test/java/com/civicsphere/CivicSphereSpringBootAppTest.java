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
import com.civicsphere.repository.ComplaintRepository;
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
    private ComplaintRepository complaintRepository;

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

    @Test
    void separateCitizenAndAdminLoginEndpointsWorkAndBlockCrossPortalAccess() throws Exception {
        // Register Citizen
        String citizenEmail = "citizen" + System.currentTimeMillis() + "@example.com";
        RegisterRequest registerReq = new RegisterRequest();
        registerReq.setFullName("Test Citizen");
        registerReq.setEmail(citizenEmail);
        registerReq.setPhone("9811223344");
        registerReq.setAddress("Kondapur, Hyderabad");
        registerReq.setPassword("CitizenPass#2026");
        registerReq.setConfirmPassword("CitizenPass#2026");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.user.role").value(User.ROLE_CITIZEN));

        // 1. Valid Citizen login via /api/auth/citizen/login
        LoginRequest citizenLogin = new LoginRequest();
        citizenLogin.setEmail(citizenEmail);
        citizenLogin.setPassword("CitizenPass#2026");

        mockMvc.perform(post("/api/auth/citizen/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(citizenLogin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.user.role").value(User.ROLE_CITIZEN));

        // 2. Cross-portal attempt: Citizen attempts Admin login -> Rejected HTTP 403
        mockMvc.perform(post("/api/auth/admin/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(citizenLogin)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Administrator privileges required")));

        // 3. Valid Admin login via /api/auth/admin/login
        LoginRequest adminLogin = new LoginRequest();
        adminLogin.setEmail("admin@civicsphere.gov.local");
        adminLogin.setPassword("SuperSecretAdmin#2026");

        mockMvc.perform(post("/api/auth/admin/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(adminLogin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.user.role").value(User.ROLE_ADMIN));

        // 4. Cross-portal attempt: Admin attempts Citizen login -> Rejected HTTP 403
        mockMvc.perform(post("/api/auth/citizen/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(adminLogin)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Administrator account detected")));

        // 5. Invalid credentials -> Rejected HTTP 401
        LoginRequest invalidLogin = new LoginRequest();
        invalidLogin.setEmail(citizenEmail);
        invalidLogin.setPassword("WrongPassword#123");

        mockMvc.perform(post("/api/auth/citizen/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidLogin)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Invalid email or password."));
    }

    @Test
    void roleBasedAccessControlAndRouteProtectionEnforced() throws Exception {
        // Unauthenticated access to admin API returns 401
        mockMvc.perform(get("/api/admin/dashboard"))
                .andExpect(status().isUnauthorized());

        // Unauthenticated access to citizen API returns 401
        mockMvc.perform(get("/api/complaints/dashboard"))
                .andExpect(status().isUnauthorized());

        // Unauthenticated access to HTML admin page redirects to /admin/login.html
        mockMvc.perform(get("/admin/dashboard.html"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/login.html"));

        // Unauthenticated access to HTML citizen page redirects to /citizen/login.html
        mockMvc.perform(get("/citizen/dashboard.html"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/citizen/login.html"));

        // Authenticate Citizen
        String citizenEmail = "citizen.rbac" + System.currentTimeMillis() + "@example.com";
        RegisterRequest registerReq = new RegisterRequest();
        registerReq.setFullName("RBAC Citizen");
        registerReq.setEmail(citizenEmail);
        registerReq.setPhone("9811223344");
        registerReq.setAddress("Kondapur, Hyderabad");
        registerReq.setPassword("CitizenPass#2026");
        registerReq.setConfirmPassword("CitizenPass#2026");

        MvcResult citizenReg = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerReq)))
                .andExpect(status().isCreated())
                .andReturn();
        MockHttpSession citizenSession = (MockHttpSession) citizenReg.getRequest().getSession();

        // Citizen accessing admin API is rejected with 403
        mockMvc.perform(get("/api/admin/dashboard").session(citizenSession))
                .andExpect(status().isForbidden());

        // Citizen accessing admin HTML redirects to citizen dashboard
        mockMvc.perform(get("/admin/dashboard.html").session(citizenSession))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/citizen/dashboard.html?error=admin_required"));

        // Authenticate Admin
        LoginRequest adminLogin = new LoginRequest();
        adminLogin.setEmail("admin@civicsphere.gov.local");
        adminLogin.setPassword("SuperSecretAdmin#2026");

        MvcResult adminRes = mockMvc.perform(post("/api/auth/admin/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(adminLogin)))
                .andExpect(status().isOk())
                .andReturn();
        MockHttpSession adminSession = (MockHttpSession) adminRes.getRequest().getSession();

        // Admin cannot enter citizen dashboard as citizen
        mockMvc.perform(get("/citizen/dashboard.html").session(adminSession))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/dashboard.html?error=citizen_only"));
    }

    @Test
    void idorProtectionPreventsCitizenFromAccessingAnotherCitizenComplaint() throws Exception {
        // Register Citizen A
        String emailA = "citizenA" + System.currentTimeMillis() + "@example.com";
        User userA = new User();
        userA.setFullName("Citizen Alpha");
        userA.setEmail(emailA);
        userA.setPhone("9100000001");
        userA.setAddress("Area A");
        userA.setPasswordHash(passwordEncoder.encode("PassA#2026"));
        userA.setRole(User.ROLE_CITIZEN);
        userA = userRepository.save(userA);

        // Register Citizen B
        String emailB = "citizenB" + System.currentTimeMillis() + "@example.com";
        User userB = new User();
        userB.setFullName("Citizen Beta");
        userB.setEmail(emailB);
        userB.setPhone("9100000002");
        userB.setAddress("Area B");
        userB.setPasswordHash(passwordEncoder.encode("PassB#2026"));
        userB.setRole(User.ROLE_CITIZEN);
        userB = userRepository.save(userB);

        // Create Complaint owned by Citizen A
        Category category = categoryRepository.findByActiveTrueOrderByNameAsc().get(0);
        Complaint complaintA = new Complaint();
        complaintA.setUser(userA);
        complaintA.setCategory(category);
        complaintA.setTitle("Broken streetlight on Alpha Street");
        complaintA.setDescription("Dark street prone to accidents.");
        complaintA.setArea("Area A");
        complaintA.setCity("Hyderabad");
        complaintA.setState("Telangana");
        complaintA.setStatus(Complaint.STATUS_SUBMITTED);
        complaintA = complaintRepository.save(complaintA);

        // Log in as Citizen B
        LoginRequest loginB = new LoginRequest();
        loginB.setEmail(emailB);
        loginB.setPassword("PassB#2026");
        MvcResult resB = mockMvc.perform(post("/api/auth/citizen/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginB)))
                .andExpect(status().isOk())
                .andReturn();
        MockHttpSession sessionB = (MockHttpSession) resB.getRequest().getSession();

        // Citizen B attempts to view Citizen A's complaint -> Rejected with 403 Forbidden
        mockMvc.perform(get("/api/complaints/" + complaintA.getComplaintId()).session(sessionB))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("You are not authorized to view this complaint."));

        // Admin logs in and views Citizen A's complaint -> Allowed 200 OK
        LoginRequest adminLogin = new LoginRequest();
        adminLogin.setEmail("admin@civicsphere.gov.local");
        adminLogin.setPassword("SuperSecretAdmin#2026");
        MvcResult adminRes = mockMvc.perform(post("/api/auth/admin/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(adminLogin)))
                .andExpect(status().isOk())
                .andReturn();
        MockHttpSession adminSession = (MockHttpSession) adminRes.getRequest().getSession();

        mockMvc.perform(get("/api/complaints/" + complaintA.getComplaintId()).session(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.complaintId").value(complaintA.getComplaintId()));
    }

    @Test
    void publicRegistrationCannotCreateAdminAndRoleCannotBeModified() throws Exception {
        // Attempt to pass "role": "ADMIN" in registration payload
        String payloadWithRole = "{" +
                "\"fullName\":\"Hacker User\"," +
                "\"email\":\"hacker" + System.currentTimeMillis() + "@example.com\"," +
                "\"phone\":\"9999999999\"," +
                "\"address\":\"Secret Address\"," +
                "\"password\":\"HackerPass#2026\"," +
                "\"confirmPassword\":\"HackerPass#2026\"," +
                "\"role\":\"ADMIN\"" +
                "}";

        MvcResult regRes = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payloadWithRole))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.user.role").value(User.ROLE_CITIZEN))
                .andReturn();

        // Verify in database/repository that role is strictly ROLE_CITIZEN
        User registered = userRepository.findByEmailIgnoreCase(
                objectMapper.readTree(regRes.getResponse().getContentAsString()).path("data").path("user").path("email").asText()
        ).orElseThrow();
        assertEquals(User.ROLE_CITIZEN, registered.getRole(), "User registered publicly must ALWAYS receive CITIZEN role");
    }

    @Test
    void corsConfigurationAndPreflightOptionsWork() throws Exception {
        mockMvc.perform(options("/api/auth/citizen/login")
                        .header("Origin", "https://civicsphere.onrender.com")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isOk())
                .andExpect(header().exists("Access-Control-Allow-Origin"));
    }

    @Test
    void viewControllersMapExtensionlessRoutes() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/login.html"));

        mockMvc.perform(get("/register"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/register.html"));

        mockMvc.perform(get("/citizen/login"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/citizen/login.html"));

        mockMvc.perform(get("/admin/login"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/admin/login.html"));
    }
}
