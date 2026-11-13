package com.civicsphere;

import com.civicsphere.config.AppProperties;
import com.civicsphere.dto.DepartmentDTO;
import com.civicsphere.model.Category;
import com.civicsphere.model.Complaint;
import com.civicsphere.model.ComplaintHistory;
import com.civicsphere.model.Department;
import com.civicsphere.model.RecurrenceRecord;
import com.civicsphere.model.User;
import com.civicsphere.repository.ComplaintHistoryRepository;
import com.civicsphere.repository.ComplaintRepository;
import com.civicsphere.repository.DepartmentRepository;
import com.civicsphere.repository.RecurrenceRecordRepository;
import com.civicsphere.repository.UserRepository;
import com.civicsphere.service.AuthService;
import com.civicsphere.service.DepartmentService;
import com.civicsphere.service.RecurrenceService;
import com.civicsphere.service.ServiceException;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.lang.reflect.Proxy;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class ServiceBusinessLogicTest {

    @SuppressWarnings("unchecked")
    private static <T> T stubRepo(Class<T> iface, java.util.function.BiFunction<String, Object[], Object> handler) {
        return (T) Proxy.newProxyInstance(
                iface.getClassLoader(),
                new Class<?>[]{iface},
                (proxy, method, args) -> {
                    Object res = handler.apply(method.getName(), args != null ? args : new Object[0]);
                    if (res == NO_MATCH) {
                        throw new UnsupportedOperationException("Unexpected call: " + iface.getSimpleName() + "." + method.getName());
                    }
                    return res;
                }
        );
    }

    private static final Object NO_MATCH = new Object();

    @Test
    void registerRejectsDuplicateEmailAndForcesCitizenRole() {
        Map<String, User> store = new HashMap<>();
        AtomicInteger seq = new AtomicInteger(40);
        PasswordEncoder encoder = new BCryptPasswordEncoder(10);

        UserRepository userRepo = stubRepo(UserRepository.class, (name, args) -> switch (name) {
            case "existsByEmailIgnoreCase" -> store.containsKey(((String) args[0]).toLowerCase());
            case "save" -> {
                User u = (User) args[0];
                if (u.getUserId() == null) {
                    u.setUserId(seq.incrementAndGet());
                }
                store.put(u.getEmail().toLowerCase(), u);
                yield u;
            }
            case "findByEmailIgnoreCase" -> Optional.ofNullable(store.get(((String) args[0]).toLowerCase()));
            default -> NO_MATCH;
        });

        AuthService authService = new AuthService(userRepo, encoder);
        User created = authService.registerCitizen("Jane Doe", "Jane@Example.com", "9876543210",
                "Plot 12, Kukatpally, Hyderabad", "Passw0rd!", "Passw0rd!");
        assertEquals(User.ROLE_CITIZEN, created.getRole());
        assertEquals("jane@example.com", created.getEmail());
        assertTrue(encoder.matches("Passw0rd!", created.getPasswordHash()));

        ServiceException dupEx = assertThrows(ServiceException.class, () ->
                authService.registerCitizen("Jane Doe", "jane@example.com", "9876543210",
                        "Plot 12, Kukatpally, Hyderabad", "Passw0rd!", "Passw0rd!"));
        assertEquals(409, dupEx.getStatusCode());
    }

    @Test
    void authenticateRejectsInvalidPassword() {
        PasswordEncoder encoder = new BCryptPasswordEncoder(10);
        User stored = new User();
        stored.setUserId(7);
        stored.setEmail("citizen@example.com");
        stored.setPasswordHash(encoder.encode("Correct#123"));
        stored.setRole(User.ROLE_CITIZEN);

        UserRepository userRepo = stubRepo(UserRepository.class, (name, args) -> switch (name) {
            case "findByEmailIgnoreCase" ->
                    "citizen@example.com".equalsIgnoreCase((String) args[0]) ? Optional.of(stored) : Optional.empty();
            default -> NO_MATCH;
        });

        AuthService authService = new AuthService(userRepo, encoder);

        ServiceException badEx = assertThrows(ServiceException.class, () ->
                authService.authenticate("citizen@example.com", "WrongPass123"));
        assertEquals(401, badEx.getStatusCode());

        User authed = authService.authenticate("citizen@example.com", "Correct#123");
        assertEquals(7, authed.getUserId());
    }

    @Test
    void departmentServicePreventsDuplicatesAndDeactivatesSafely() {
        Map<Integer, Department> depts = new HashMap<>();
        Department existing = new Department();
        existing.setDepartmentId(3);
        existing.setName("Sanitation");
        existing.setActive(true);
        depts.put(3, existing);

        DepartmentRepository deptRepo = stubRepo(DepartmentRepository.class, (name, args) -> switch (name) {
            case "existsByNameIgnoreCase" ->
                    depts.values().stream().anyMatch(d -> d.getName().equalsIgnoreCase((String) args[0]));
            case "findById" -> Optional.ofNullable(depts.get(args[0]));
            case "save" -> {
                Department d = (Department) args[0];
                depts.put(d.getDepartmentId(), d);
                yield d;
            }
            case "countAssignedComplaintsByDepartment" -> List.<Object[]>of();
            default -> NO_MATCH;
        });

        DepartmentService service = new DepartmentService(deptRepo);

        ServiceException dupEx = assertThrows(ServiceException.class, () ->
                service.createDepartment("Sanitation", "Handles sanitation", "sanitation@gov.local", "9876543210"));
        assertEquals(409, dupEx.getStatusCode());

        DepartmentDTO res = service.deactivateDepartment(3);
        assertFalse(res.isActive());
    }

    @Test
    void recurrenceServicePreventsDuplicatePairsDuringDetectionAndManualLink() {
        Instant now = Instant.now();
        Category road = new Category();
        road.setCategoryId(1);
        road.setName("Road Damage");

        User reporter = new User();
        reporter.setUserId(15);
        reporter.setFullName("Citizen Reporter");
        reporter.setRole(User.ROLE_CITIZEN);

        Complaint c101 = new Complaint();
        c101.setComplaintId(101);
        c101.setCategory(road);
        c101.setTitle("Pothole in Kukatpally main road");
        c101.setDescription("Deep pothole causing traffic congestion near JNTU Metro station.");
        c101.setArea("Kukatpally");
        c101.setCity("Hyderabad");
        c101.setState("Telangana");
        c101.setStatus(Complaint.STATUS_RESOLVED);
        c101.setSubmittedAt(now.minus(10, ChronoUnit.DAYS));
        c101.setResolvedAt(now.minus(7, ChronoUnit.DAYS));

        Complaint c145 = new Complaint();
        c145.setComplaintId(145);
        c145.setCategory(road);
        c145.setUser(reporter);
        c145.setTitle("Pothole returned in Kukatpally main road");
        c145.setDescription("Deep pothole causing traffic congestion near JNTU Metro station again.");
        c145.setArea("Kukatpally");
        c145.setCity("Hyderabad");
        c145.setState("Telangana");
        c145.setStatus(Complaint.STATUS_SUBMITTED);
        c145.setSubmittedAt(now);

        Set<String> pairs = new HashSet<>();
        List<RecurrenceRecord> createdRecords = new ArrayList<>();
        AtomicInteger recSeq = new AtomicInteger();

        ComplaintRepository complaintRepo = stubRepo(ComplaintRepository.class, (name, args) -> switch (name) {
            case "findEligibleResolvedCandidates" -> List.of(c101);
            case "save" -> args[0];
            default -> NO_MATCH;
        });

        RecurrenceRecordRepository recRepo = stubRepo(RecurrenceRecordRepository.class, (name, args) -> switch (name) {
            case "existsByOriginalComplaintComplaintIdAndRecurringComplaintComplaintId" ->
                    pairs.contains(args[0] + ":" + args[1]);
            case "save" -> {
                RecurrenceRecord record = (RecurrenceRecord) args[0];
                pairs.add(record.getOriginalComplaint().getComplaintId() + ":" +
                        record.getRecurringComplaint().getComplaintId());
                record.setRecurrenceId(recSeq.incrementAndGet());
                createdRecords.add(record);
                yield record;
            }
            default -> NO_MATCH;
        });

        ComplaintHistoryRepository historyRepo = stubRepo(ComplaintHistoryRepository.class, (name, args) -> switch (name) {
            case "save" -> {
                ComplaintHistory h = (ComplaintHistory) args[0];
                h.setHistoryId(1);
                yield h;
            }
            default -> NO_MATCH;
        });

        RecurrenceService service = new RecurrenceService(
                complaintRepo, recRepo, historyRepo, null, new AppProperties());

        List<RecurrenceRecord> created = service.detectAndRecordRecurrences(c145, reporter);
        assertEquals(1, created.size());
        assertNotNull(c145.getParentComplaint());
        assertEquals(101, c145.getParentComplaint().getComplaintId());
        assertTrue(created.get(0).getMatchingScore() >= 70);

        RecurrenceRecord duplicateManual = service.createManualRecurrenceLink(c101, c145, reporter);
        assertNull(duplicateManual);
        assertEquals(1, createdRecords.size());
    }
}
