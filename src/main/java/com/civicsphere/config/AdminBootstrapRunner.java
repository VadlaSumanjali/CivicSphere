package com.civicsphere.config;

import com.civicsphere.model.Category;
import com.civicsphere.model.Complaint;
import com.civicsphere.model.ComplaintHistory;
import com.civicsphere.model.Department;
import com.civicsphere.model.User;
import com.civicsphere.repository.CategoryRepository;
import com.civicsphere.repository.ComplaintHistoryRepository;
import com.civicsphere.repository.ComplaintRepository;
import com.civicsphere.repository.DepartmentRepository;
import com.civicsphere.repository.UserRepository;
import com.civicsphere.service.RecurrenceService;
import com.civicsphere.util.ValidationUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Optional CLI/environment-driven bootstrapper for creating an ADMIN account
 * and realistic demo complaints without storing plaintext credentials in source control.
 *
 * Usage:
 *   java -jar target/civicsphere.jar --civicsphere.setup=true [--civicsphere.seed-demo=true]
 * Requires env vars: CIVICSPHERE_ADMIN_EMAIL, CIVICSPHERE_ADMIN_PASSWORD (min 8 chars).
 */
@Component
public class AdminBootstrapRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrapRunner.class);

    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final DepartmentRepository departmentRepository;
    private final ComplaintRepository complaintRepository;
    private final ComplaintHistoryRepository historyRepository;
    private final RecurrenceService recurrenceService;
    private final PasswordEncoder passwordEncoder;

    public AdminBootstrapRunner(UserRepository userRepository,
                                CategoryRepository categoryRepository,
                                DepartmentRepository departmentRepository,
                                ComplaintRepository complaintRepository,
                                ComplaintHistoryRepository historyRepository,
                                RecurrenceService recurrenceService,
                                PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.categoryRepository = categoryRepository;
        this.departmentRepository = departmentRepository;
        this.complaintRepository = complaintRepository;
        this.historyRepository = historyRepository;
        this.recurrenceService = recurrenceService;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        boolean setup = args.containsOption("civicsphere.setup")
                || "true".equalsIgnoreCase(System.getenv("CIVICSPHERE_SETUP"));
        boolean seedDemo = args.containsOption("civicsphere.seed-demo")
                || "true".equalsIgnoreCase(System.getenv("CIVICSPHERE_SEED_DEMO"));

        if (!setup && !seedDemo) {
            return;
        }

        String adminEmail = System.getenv("CIVICSPHERE_ADMIN_EMAIL");
        String adminPass = System.getenv("CIVICSPHERE_ADMIN_PASSWORD");
        String rawAdminName = System.getenv("CIVICSPHERE_ADMIN_NAME");
        final String adminName = ValidationUtil.isBlank(rawAdminName)
                ? "Municipal Commissioner"
                : rawAdminName.trim();

        if (!ValidationUtil.isValidEmail(adminEmail) || !ValidationUtil.isValidPassword(adminPass)) {
            log.warn("CIVICSPHERE_SETUP requested, but CIVICSPHERE_ADMIN_EMAIL / CIVICSPHERE_ADMIN_PASSWORD are invalid or unset.");
            return;
        }

        User admin = userRepository.findByEmailIgnoreCase(adminEmail.trim()).orElseGet(() -> {
            User created = new User();
            created.setFullName(adminName);
            created.setEmail(adminEmail.trim().toLowerCase());
            created.setPhone("9000000001");
            created.setAddress("CivicSphere Municipal Command Center");
            created.setPasswordHash(passwordEncoder.encode(adminPass));
            created.setRole(User.ROLE_ADMIN);
            return userRepository.save(created);
        });
        log.info("Administrator account verified: {}", admin.getEmail());

        if (!seedDemo) {
            return;
        }

        String citizenEmail = System.getenv("CIVICSPHERE_DEMO_CITIZEN_EMAIL");
        String citizenPass = System.getenv("CIVICSPHERE_DEMO_CITIZEN_PASSWORD");
        if (!ValidationUtil.isValidEmail(citizenEmail) || !ValidationUtil.isValidPassword(citizenPass)) {
            log.warn("CIVICSPHERE_SEED_DEMO requested, but CIVICSPHERE_DEMO_CITIZEN_EMAIL / CIVICSPHERE_DEMO_CITIZEN_PASSWORD are invalid or unset.");
            return;
        }

        User citizen = userRepository.findByEmailIgnoreCase(citizenEmail.trim()).orElseGet(() -> {
            User created = new User();
            created.setFullName("Ananya Rao");
            created.setEmail(citizenEmail.trim().toLowerCase());
            created.setPhone("9876543210");
            created.setAddress("Kukatpally Housing Board, Hyderabad, Telangana");
            created.setPasswordHash(passwordEncoder.encode(citizenPass));
            created.setRole(User.ROLE_CITIZEN);
            return userRepository.save(created);
        });

        List<Category> categories = categoryRepository.findByActiveTrueOrderByNameAsc();
        List<Department> departments = departmentRepository.findByActiveTrueOrderByNameAsc();
        if (categories.isEmpty() || departments.isEmpty()) {
            log.warn("Cannot seed demo complaints without seeded categories and departments.");
            return;
        }

        Category roadCategory = categories.stream()
                .filter(c -> c.getName().toLowerCase().contains("road"))
                .findFirst()
                .orElse(categories.get(0));
        Department roadDept = departments.stream()
                .filter(d -> d.getName().toLowerCase().contains("road"))
                .findFirst()
                .orElse(departments.get(0));

        Instant now = Instant.now();
        Complaint historical = new Complaint();
        historical.setUser(citizen);
        historical.setCategory(roadCategory);
        historical.setDepartment(roadDept);
        historical.setTitle("Deep pothole in Kukatpally near JNTU Metro");
        historical.setDescription("Large water-filled pothole on the main road near JNTU Metro causing two-wheeler skids and heavy traffic congestion.");
        historical.setArea("Kukatpally");
        historical.setCity("Hyderabad");
        historical.setState("Telangana");
        historical.setLandmark("Near JNTU Metro Station");
        historical.setLatitude(17.4948000);
        historical.setLongitude(78.3996000);
        historical.setStatus(Complaint.STATUS_RESOLVED);
        historical.setSubmittedAt(now.minus(15, ChronoUnit.DAYS));
        historical.setResolvedAt(now.minus(10, ChronoUnit.DAYS));
        historical.setResolutionRemarks("Bitumen patchwork completed by Road Maintenance crew.");
        historical.setResolvedByUser(admin);
        Complaint savedHistorical = complaintRepository.save(historical);

        ComplaintHistory h1 = new ComplaintHistory();
        h1.setComplaint(savedHistorical);
        h1.setPreviousStatus(Complaint.STATUS_IN_PROGRESS);
        h1.setNewStatus(Complaint.STATUS_RESOLVED);
        h1.setRemarks("Bitumen patchwork completed by Road Maintenance crew.");
        h1.setUpdatedByUser(admin);
        historyRepository.save(h1);

        Complaint recurring = new Complaint();
        recurring.setUser(citizen);
        recurring.setCategory(roadCategory);
        recurring.setTitle("Pothole reappeared at Kukatpally JNTU Metro main road");
        recurring.setDescription("Deep pothole reopened on the main road near JNTU Metro after recent rain causing two-wheeler skids and traffic congestion.");
        recurring.setArea("Kukatpally");
        recurring.setCity("Hyderabad");
        recurring.setState("Telangana");
        recurring.setLandmark("JNTU Metro Station");
        recurring.setLatitude(17.4949500);
        recurring.setLongitude(78.3997200);
        recurring.setStatus(Complaint.STATUS_SUBMITTED);
        recurring.setSubmittedAt(now);
        Complaint savedRecurring = complaintRepository.save(recurring);

        recurrenceService.detectAndRecordRecurrences(savedRecurring, citizen);
        log.info("Seeded historical Complaint #{} and recurring Complaint #{}.",
                savedHistorical.getComplaintId(), savedRecurring.getComplaintId());
    }
}
