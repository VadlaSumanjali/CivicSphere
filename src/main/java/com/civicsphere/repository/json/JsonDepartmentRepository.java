package com.civicsphere.repository.json;

import com.civicsphere.model.Complaint;
import com.civicsphere.model.Department;
import com.civicsphere.repository.ComplaintRepository;
import com.civicsphere.repository.DepartmentRepository;
import com.civicsphere.storage.JsonStorage;
import com.fasterxml.jackson.core.type.TypeReference;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@Repository
public class JsonDepartmentRepository implements DepartmentRepository {

    private static final String FILENAME = "departments.json";

    private final JsonStorage storage;
    private final ObjectProvider<ComplaintRepository> complaintRepositoryProvider;
    private final Map<Integer, Department> departments = new ConcurrentHashMap<>();
    private final AtomicInteger idSeq = new AtomicInteger(0);
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    public JsonDepartmentRepository(JsonStorage storage,
                                    ObjectProvider<ComplaintRepository> complaintRepositoryProvider) {
        this.storage = storage;
        this.complaintRepositoryProvider = complaintRepositoryProvider;
    }

    @PostConstruct
    public void init() {
        lock.writeLock().lock();
        try {
            List<Department> loaded = storage.loadList(FILENAME, new TypeReference<List<Department>>() {});
            int maxId = 0;
            for (Department d : loaded) {
                if (d.getDepartmentId() != null) {
                    departments.put(d.getDepartmentId(), d);
                    if (d.getDepartmentId() > maxId) {
                        maxId = d.getDepartmentId();
                    }
                }
            }
            idSeq.set(maxId);
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public List<Department> findAll() {
        lock.readLock().lock();
        try {
            return new ArrayList<>(departments.values());
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public Optional<Department> findById(Integer departmentId) {
        if (departmentId == null) return Optional.empty();
        lock.readLock().lock();
        try {
            return Optional.ofNullable(departments.get(departmentId));
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public Department save(Department department) {
        lock.writeLock().lock();
        try {
            Instant now = Instant.now();
            if (department.getDepartmentId() == null || department.getDepartmentId() <= 0) {
                department.setDepartmentId(idSeq.incrementAndGet());
            } else if (department.getDepartmentId() > idSeq.get()) {
                idSeq.set(department.getDepartmentId());
            }
            if (department.getCreatedAt() == null) {
                department.setCreatedAt(now);
            }
            department.setUpdatedAt(now);
            departments.put(department.getDepartmentId(), department);
            storage.saveList(FILENAME, new ArrayList<>(departments.values()));
            return department;
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public List<Department> findByActiveTrueOrderByNameAsc() {
        lock.readLock().lock();
        try {
            return departments.values().stream()
                    .filter(Department::isActive)
                    .sorted(Comparator.comparing(Department::getName, String.CASE_INSENSITIVE_ORDER))
                    .toList();
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public List<Department> findAllByOrderByNameAsc() {
        lock.readLock().lock();
        try {
            return departments.values().stream()
                    .sorted(Comparator.comparing(Department::getName, String.CASE_INSENSITIVE_ORDER))
                    .toList();
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public boolean existsByNameIgnoreCase(String name) {
        if (name == null) return false;
        String clean = name.trim();
        lock.readLock().lock();
        try {
            return departments.values().stream()
                    .anyMatch(d -> d.getName() != null && d.getName().equalsIgnoreCase(clean));
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public boolean existsByNameIgnoreCaseAndDepartmentIdNot(String name, Integer departmentId) {
        if (name == null) return false;
        String clean = name.trim();
        lock.readLock().lock();
        try {
            return departments.values().stream()
                    .anyMatch(d -> d.getName() != null && d.getName().equalsIgnoreCase(clean)
                            && !Objects.equals(d.getDepartmentId(), departmentId));
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public long countByActiveTrue() {
        lock.readLock().lock();
        try {
            return departments.values().stream().filter(Department::isActive).count();
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public long count() {
        lock.readLock().lock();
        try {
            return departments.size();
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public List<Object[]> countAssignedComplaintsByDepartment() {
        ComplaintRepository complaintRepo = complaintRepositoryProvider.getIfAvailable();
        if (complaintRepo == null) {
            return List.of();
        }
        Map<Integer, Long> counts = new HashMap<>();
        for (Complaint c : complaintRepo.findAll()) {
            Integer deptId = c.getDepartmentId();
            if (deptId != null) {
                counts.merge(deptId, 1L, Long::sum);
            }
        }
        List<Object[]> rows = new ArrayList<>();
        for (Map.Entry<Integer, Long> entry : counts.entrySet()) {
            rows.add(new Object[]{entry.getKey(), entry.getValue()});
        }
        return rows;
    }
}
