package com.civicsphere;

import com.civicsphere.config.AppProperties;
import com.civicsphere.dto.ComplaintDTO;
import com.civicsphere.dto.DepartmentDTO;
import com.civicsphere.dto.PagedResponse;
import com.civicsphere.dto.RecurrenceDTO;
import com.civicsphere.model.Category;
import com.civicsphere.model.Complaint;
import com.civicsphere.model.ComplaintHistory;
import com.civicsphere.model.Department;
import com.civicsphere.model.RecurrenceRecord;
import com.civicsphere.model.User;
import com.civicsphere.repository.CategoryRepository;
import com.civicsphere.repository.ComplaintHistoryRepository;
import com.civicsphere.repository.ComplaintRepository;
import com.civicsphere.repository.DepartmentRepository;
import com.civicsphere.repository.RecurrenceRecordRepository;
import com.civicsphere.repository.UserRepository;
import com.civicsphere.repository.json.JsonCategoryRepository;
import com.civicsphere.repository.json.JsonComplaintHistoryRepository;
import com.civicsphere.repository.json.JsonComplaintRepository;
import com.civicsphere.repository.json.JsonDepartmentRepository;
import com.civicsphere.repository.json.JsonRecurrenceRecordRepository;
import com.civicsphere.repository.json.JsonUserRepository;
import com.civicsphere.service.AdminService;
import com.civicsphere.service.AuthService;
import com.civicsphere.service.ComplaintService;
import com.civicsphere.service.DepartmentService;
import com.civicsphere.service.RecurrenceService;
import com.civicsphere.storage.JsonStorage;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Full End-to-End Workflow & Persistence Test for CivicSphere Zero-Database Architecture.
 * Tests:
 * 1. Data initialization (categories, departments)
 * 2. Citizen registration & authentication (BCrypt)
 * 3. Citizen submits complaint
 * 4. Admin assigns department
 * 5. Admin updates status with valid transitions
 * 6. Complaint history verification
 * 7. Recurrence detection of reopened/repeated civic issues
 * 8. Admin recurrence verification
 * 9. Persistence across application stop and restart
 */
class CivicSphereEndToEndTest {

    @TempDir
    Path tempDir;

    private ObjectMapper objectMapper;
    private AppProperties appProperties;
    private PasswordEncoder passwordEncoder;

    private JsonStorage jsonStorage;
    private JsonCategoryRepository categoryRepo;
    private JsonDepartmentRepository departmentRepo;
    private JsonUserRepository userRepo;
    private JsonComplaintRepository complaintRepo;
    private JsonComplaintHistoryRepository historyRepo;
    private JsonRecurrenceRecordRepository recurrenceRepo;

    private AuthService authService;
    private DepartmentService departmentService;
    private RecurrenceService recurrenceService;
    private ComplaintService complaintService;
    private AdminService adminService;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        appProperties = new AppProperties();
        appProperties.setDataDir(tempDir.toString());
        appProperties.setUploadDir(tempDir.resolve("uploads").toString());

        passwordEncoder = new BCryptPasswordEncoder(10);

        initRepositoriesAndServices();
    }

    private void initRepositoriesAndServices() {
        jsonStorage = new JsonStorage(appProperties, objectMapper);

        categoryRepo = new JsonCategoryRepository(jsonStorage);
        categoryRepo.init();

        userRepo = new JsonUserRepository(jsonStorage);
        userRepo.init();

        // ObjectProvider wrapper for ComplaintRepository to pass to departmentRepo
        ObjectProvider<ComplaintRepository> complaintProvider = new SimpleObjectProvider<>(() -> complaintRepo);
        departmentRepo = new JsonDepartmentRepository(jsonStorage, complaintProvider);
        departmentRepo.init();

        ObjectProvider<RecurrenceRecordRepository> recProvider = new SimpleObjectProvider<>(() -> recurrenceRepo);
        complaintRepo = new JsonComplaintRepository(jsonStorage, userRepo, categoryRepo, departmentRepo, recProvider);
        complaintRepo.init();

        historyRepo = new JsonComplaintHistoryRepository(jsonStorage, complaintRepo, userRepo);
        historyRepo.init();

        recurrenceRepo = new JsonRecurrenceRecordRepository(jsonStorage, complaintRepo, userRepo);
        recurrenceRepo.init();

        authService = new AuthService(userRepo, passwordEncoder);
        departmentService = new DepartmentService(departmentRepo);
        recurrenceService = new RecurrenceService(complaintRepo, recurrenceRepo, historyRepo, userRepo, appProperties);
        complaintService = new ComplaintService(complaintRepo, historyRepo, categoryRepo, departmentRepo,
                recurrenceRepo, userRepo, recurrenceService, appProperties);
        adminService = new AdminService(complaintRepo, recurrenceRepo, userRepo, complaintService, recurrenceService);
    }

    @Test
    void testFullCitizenAndAdminWorkflowWithRecurrenceAndPersistenceRestart() {
        // Step 1: Verify Default Data Seeding
        List<Category> categories = categoryRepo.findByActiveTrueOrderByNameAsc();
        assertFalse(categories.isEmpty(), "Default categories must be automatically initialized");
        assertEquals(7, categories.size());

        List<DepartmentDTO> departments = departmentService.listDepartments(true);
        assertFalse(departments.isEmpty(), "Default departments must be automatically initialized");
        assertEquals(5, departments.size());

        // Step 2: Register Citizen and Admin
        User citizen = authService.registerCitizen(
                "Ramesh Kumar", "ramesh@example.com", "9876543210",
                "Miyapur Main Road, Hyderabad", "Citizen#Pass2026", "Citizen#Pass2026"
        );
        assertNotNull(citizen.getUserId());
        assertEquals(User.ROLE_CITIZEN, citizen.getRole());

        // Authenticate citizen
        User authedCitizen = authService.authenticate("ramesh@example.com", "Citizen#Pass2026");
        assertEquals(citizen.getUserId(), authedCitizen.getUserId());

        // Create Admin user
        User admin = new User();
        admin.setFullName("Super Administrator");
        admin.setEmail("admin@civicsphere.gov.local");
        admin.setPhone("9000000001");
        admin.setAddress("Headquarters");
        admin.setPasswordHash(passwordEncoder.encode("Admin#Secure2026"));
        admin.setRole(User.ROLE_ADMIN);
        admin = userRepo.save(admin);
        assertNotNull(admin.getUserId());

        // Step 3: Citizen Submits Complaint #1 (Pothole at Miyapur)
        Category roadCategory = categories.stream()
                .filter(c -> c.getName().toLowerCase().contains("road"))
                .findFirst().orElseThrow();

        ComplaintDTO c1 = complaintService.submitComplaint(
                citizen.getUserId(),
                roadCategory.getCategoryId(),
                "Massive pothole near Miyapur Metro Pillar 42",
                "Deep crater in the tarmac causing traffic bottlenecks and bike accidents.",
                "Miyapur",
                "Hyderabad",
                "Telangana",
                "Near Metro Pillar 42",
                17.4960,
                78.3580,
                null,
                null
        );
        assertNotNull(c1.getComplaintId());
        assertEquals(Complaint.STATUS_SUBMITTED, c1.getStatus());

        // Step 4: Admin assigns department (Road Maintenance)
        Department roadDept = departmentRepo.findByActiveTrueOrderByNameAsc().stream()
                .filter(d -> d.getName().toLowerCase().contains("road"))
                .findFirst().orElseThrow();

        ComplaintDTO assignedDto = complaintService.assignDepartment(
                c1.getComplaintId(), roadDept.getDepartmentId(), "Dispatching repair crew", admin.getUserId());
        assertEquals(Complaint.STATUS_ASSIGNED, assignedDto.getStatus());
        assertEquals(roadDept.getName(), assignedDto.getDepartmentName());

        // Step 5: Admin updates status: ASSIGNED -> IN_PROGRESS -> RESOLVED
        ComplaintDTO inProgressDto = complaintService.updateStatus(
                c1.getComplaintId(), Complaint.STATUS_IN_PROGRESS, "Asphalt team on site", null, admin.getUserId());
        assertEquals(Complaint.STATUS_IN_PROGRESS, inProgressDto.getStatus());

        ComplaintDTO resolvedDto = complaintService.updateStatus(
                c1.getComplaintId(), Complaint.STATUS_RESOLVED, "Bitumen resurfacing complete", null, admin.getUserId());
        assertEquals(Complaint.STATUS_RESOLVED, resolvedDto.getStatus());
        assertNotNull(resolvedDto.getResolvedAt());
        assertEquals("Bitumen resurfacing complete", resolvedDto.getResolutionRemarks());

        // Step 6: Verify complaint history entries
        List<ComplaintHistory> historyList = historyRepo.findByComplaintComplaintIdOrderByUpdatedAtAscHistoryIdAsc(c1.getComplaintId());
        assertEquals(4, historyList.size(), "History should record submitted, assigned, in_progress, and resolved stages");
        assertEquals(Complaint.STATUS_SUBMITTED, historyList.get(0).getNewStatus());
        assertEquals(Complaint.STATUS_ASSIGNED, historyList.get(1).getNewStatus());
        assertEquals(Complaint.STATUS_IN_PROGRESS, historyList.get(2).getNewStatus());
        assertEquals(Complaint.STATUS_RESOLVED, historyList.get(3).getNewStatus());

        // Step 7: Citizen Submits Complaint #2 at the exact same location (Pothole reappeared)
        ComplaintDTO c2 = complaintService.submitComplaint(
                citizen.getUserId(),
                roadCategory.getCategoryId(),
                "Pothole reopened near Miyapur Metro Pillar 42",
                "The pothole repaired last week near Miyapur Metro Pillar 42 has reopened after heavy rains.",
                "Miyapur",
                "Hyderabad",
                "Telangana",
                "Metro Pillar 42",
                17.4962,
                78.3581,
                null,
                null
        );
        assertNotNull(c2.getComplaintId());

        // Step 8: Verify Recurrence Engine detected the recurrence!
        List<RecurrenceRecord> recRecords = recurrenceRepo.findAll();
        assertEquals(1, recRecords.size(), "Should automatically detect recurrence with Complaint #1");
        RecurrenceRecord recurrence = recRecords.get(0);
        assertEquals(c1.getComplaintId(), recurrence.getOriginalComplaintId());
        assertEquals(c2.getComplaintId(), recurrence.getRecurringComplaintId());
        assertTrue(recurrence.getMatchingScore() >= 70, "Recurrence score should be >= 70, got: " + recurrence.getMatchingScore());
        assertEquals(RecurrenceRecord.STATUS_PENDING, recurrence.getVerificationStatus());

        // Verify that c2 has parentComplaint linked to c1
        Complaint c2Entity = complaintRepo.findById(c2.getComplaintId()).orElseThrow();
        assertNotNull(c2Entity.getParentComplaint());
        assertEquals(c1.getComplaintId(), c2Entity.getParentComplaint().getComplaintId());

        // Step 9: Admin verifies the recurrence (CONFIRMED)
        RecurrenceDTO verifiedRec = recurrenceService.verifyRecurrence(
                recurrence.getRecurrenceId(), RecurrenceRecord.STATUS_CONFIRMED, "Confirmed recurrence of unresolved foundation issue", admin.getUserId());
        assertEquals(RecurrenceRecord.STATUS_CONFIRMED, verifiedRec.getVerificationStatus());
        assertEquals("Super Administrator", verifiedRec.getVerifiedByName());

        // Verify Admin Dashboard Analytics
        Map<String, Object> adminDash = adminService.getDashboardAnalytics();
        assertEquals(2L, adminDash.get("totalComplaints"));
        assertEquals(1L, adminDash.get("resolvedComplaints"));
        assertEquals(1L, adminDash.get("pendingComplaints"));
        assertEquals(1L, adminDash.get("confirmedRecurringIssues"));

        // Verify Citizen Dashboard
        Map<String, Object> citizenDash = complaintService.getCitizenDashboard(citizen.getUserId());
        assertEquals(2L, citizenDash.get("totalComplaints"));
        assertEquals(1L, citizenDash.get("possibleRecurringIssues"));

        // =========================================================================
        // Step 10: PERSISTENCE TEST - STOP AND RESTART APPLICATION
        // Re-initialize a brand new set of repositories pointing to the SAME tempDir
        // to simulate a complete application stop and cold restart.
        // =========================================================================
        initRepositoriesAndServices();

        // Verify all data survived restart!
        assertEquals(7, categoryRepo.findAll().size(), "Categories preserved across restart");
        assertEquals(5, departmentRepo.findAll().size(), "Departments preserved across restart");
        assertEquals(2, userRepo.findAll().size(), "Users preserved across restart");
        assertEquals(2, complaintRepo.findAll().size(), "Complaints preserved across restart");
        assertEquals(7, historyRepo.findAll().size(), "Complaint history records preserved across restart");
        assertEquals(4, historyRepo.findByComplaintComplaintIdOrderByUpdatedAtAscHistoryIdAsc(c1.getComplaintId()).size());
        assertEquals(3, historyRepo.findByComplaintComplaintIdOrderByUpdatedAtAscHistoryIdAsc(c2.getComplaintId()).size());
        assertEquals(1, recurrenceRepo.findAll().size(), "Recurrence records preserved across restart");

        // Verify authenticated user credentials work after restart
        User reloadedCitizen = authService.authenticate("ramesh@example.com", "Citizen#Pass2026");
        assertNotNull(reloadedCitizen);
        assertEquals(citizen.getUserId(), reloadedCitizen.getUserId());

        // Verify complaint details and resolved relationships after restart
        ComplaintDTO reloadedC1 = complaintService.getComplaintDetails(c1.getComplaintId(), admin);
        assertEquals("Massive pothole near Miyapur Metro Pillar 42", reloadedC1.getTitle());
        assertEquals(Complaint.STATUS_RESOLVED, reloadedC1.getStatus());
        assertEquals("Road Maintenance", reloadedC1.getDepartmentName());
        assertEquals(citizen.getUserId(), reloadedC1.getUserId());
        assertEquals("Ramesh Kumar", reloadedC1.getCitizenName());

        ComplaintDTO reloadedC2 = complaintService.getComplaintDetails(c2.getComplaintId(), admin);
        assertEquals("Pothole reopened near Miyapur Metro Pillar 42", reloadedC2.getTitle());
        assertEquals(c1.getComplaintId(), reloadedC2.getParentComplaintId());

        // Verify recurrence record after restart
        RecurrenceRecord reloadedRec = recurrenceRepo.findById(recurrence.getRecurrenceId()).orElseThrow();
        assertEquals(RecurrenceRecord.STATUS_CONFIRMED, reloadedRec.getVerificationStatus());
        assertEquals(admin.getUserId(), reloadedRec.getVerifiedByUserId());
        assertEquals("Super Administrator", reloadedRec.getVerifiedByUser().getFullName());
    }

    private static class SimpleObjectProvider<T> implements ObjectProvider<T> {
        private final java.util.function.Supplier<T> supplier;

        SimpleObjectProvider(java.util.function.Supplier<T> supplier) {
            this.supplier = supplier;
        }

        @Override
        public T getObject(Object... args) {
            return supplier.get();
        }

        @Override
        public T getIfAvailable() {
            return supplier.get();
        }

        @Override
        public T getIfUnique() {
            return supplier.get();
        }

        @Override
        public T getObject() {
            return supplier.get();
        }
    }
}
