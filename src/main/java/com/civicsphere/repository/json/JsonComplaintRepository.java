package com.civicsphere.repository.json;

import com.civicsphere.dto.PagedResult;
import com.civicsphere.model.Category;
import com.civicsphere.model.Complaint;
import com.civicsphere.model.RecurrenceRecord;
import com.civicsphere.repository.CategoryRepository;
import com.civicsphere.repository.ComplaintRepository;
import com.civicsphere.repository.DepartmentRepository;
import com.civicsphere.repository.RecurrenceRecordRepository;
import com.civicsphere.repository.UserRepository;
import com.civicsphere.storage.JsonStorage;
import com.fasterxml.jackson.core.type.TypeReference;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@Repository
public class JsonComplaintRepository implements ComplaintRepository {

    private static final String FILENAME = "complaints.json";

    private final JsonStorage storage;
    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final DepartmentRepository departmentRepository;
    private final ObjectProvider<RecurrenceRecordRepository> recurrenceRecordRepositoryProvider;

    private final Map<Integer, Complaint> complaints = new ConcurrentHashMap<>();
    private final AtomicInteger idSeq = new AtomicInteger(0);
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    public JsonComplaintRepository(JsonStorage storage,
                                   UserRepository userRepository,
                                   CategoryRepository categoryRepository,
                                   DepartmentRepository departmentRepository,
                                   ObjectProvider<RecurrenceRecordRepository> recurrenceRecordRepositoryProvider) {
        this.storage = storage;
        this.userRepository = userRepository;
        this.categoryRepository = categoryRepository;
        this.departmentRepository = departmentRepository;
        this.recurrenceRecordRepositoryProvider = recurrenceRecordRepositoryProvider;
    }

    @PostConstruct
    public void init() {
        lock.writeLock().lock();
        try {
            List<Complaint> loaded = storage.loadList(FILENAME, new TypeReference<List<Complaint>>() {});
            int maxId = 0;
            for (Complaint c : loaded) {
                if (c.getComplaintId() != null) {
                    complaints.put(c.getComplaintId(), c);
                    if (c.getComplaintId() > maxId) {
                        maxId = c.getComplaintId();
                    }
                }
            }
            idSeq.set(maxId);

            for (Complaint c : complaints.values()) {
                resolveReferences(c);
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    private void resolveReferences(Complaint c) {
        if (c.getUserId() != null && c.getUser() == null) {
            c.setUser(userRepository.findById(c.getUserId()).orElse(null));
        }
        if (c.getCategoryId() != null && c.getCategory() == null) {
            c.setCategory(categoryRepository.findById(c.getCategoryId()).orElse(null));
        }
        if (c.getDepartmentId() != null && c.getDepartment() == null) {
            c.setDepartment(departmentRepository.findById(c.getDepartmentId()).orElse(null));
        }
        if (c.getParentComplaintId() != null && c.getParentComplaint() == null) {
            c.setParentComplaint(complaints.get(c.getParentComplaintId()));
        }
        if (c.getResolvedByUserId() != null && c.getResolvedByUser() == null) {
            c.setResolvedByUser(userRepository.findById(c.getResolvedByUserId()).orElse(null));
        }
    }

    @Override
    public List<Complaint> findAll() {
        lock.readLock().lock();
        try {
            return new ArrayList<>(complaints.values());
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public Optional<Complaint> findById(Integer complaintId) {
        if (complaintId == null) return Optional.empty();
        lock.readLock().lock();
        try {
            Complaint c = complaints.get(complaintId);
            if (c != null) {
                resolveReferences(c);
            }
            return Optional.ofNullable(c);
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public Complaint save(Complaint complaint) {
        lock.writeLock().lock();
        try {
            Instant now = Instant.now();
            if (complaint.getComplaintId() == null || complaint.getComplaintId() <= 0) {
                complaint.setComplaintId(idSeq.incrementAndGet());
            } else if (complaint.getComplaintId() > idSeq.get()) {
                idSeq.set(complaint.getComplaintId());
            }
            if (complaint.getSubmittedAt() == null) {
                complaint.setSubmittedAt(now);
            }
            complaint.setUpdatedAt(now);
            if (complaint.getStatus() == null) {
                complaint.setStatus(Complaint.STATUS_SUBMITTED);
            }

            // Sync IDs from linked objects
            if (complaint.getUser() != null) complaint.setUserId(complaint.getUser().getUserId());
            if (complaint.getCategory() != null) complaint.setCategoryId(complaint.getCategory().getCategoryId());
            if (complaint.getDepartment() != null) complaint.setDepartmentId(complaint.getDepartment().getDepartmentId());
            if (complaint.getParentComplaint() != null) complaint.setParentComplaintId(complaint.getParentComplaint().getComplaintId());
            if (complaint.getResolvedByUser() != null) complaint.setResolvedByUserId(complaint.getResolvedByUser().getUserId());

            complaints.put(complaint.getComplaintId(), complaint);
            storage.saveList(FILENAME, new ArrayList<>(complaints.values()));

            resolveReferences(complaint);
            return complaint;
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public long count() {
        lock.readLock().lock();
        try {
            return complaints.size();
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public Optional<Complaint> findDetailedByComplaintId(Integer complaintId) {
        return findById(complaintId);
    }

    @Override
    public List<Complaint> findEligibleResolvedCandidates(Integer categoryId, Integer excludeId, Instant submittedAt) {
        lock.readLock().lock();
        try {
            return complaints.values().stream()
                    .filter(c -> {
                        resolveReferences(c);
                        if (!Objects.equals(c.getCategoryId(), categoryId)) return false;
                        if (Objects.equals(c.getComplaintId(), excludeId)) return false;
                        if (!Complaint.STATUS_RESOLVED.equals(c.getStatus()) && !Complaint.STATUS_CLOSED.equals(c.getStatus())) {
                            return false;
                        }
                        return c.getSubmittedAt() != null && submittedAt != null && c.getSubmittedAt().isBefore(submittedAt);
                    })
                    .sorted((c1, c2) -> {
                        Instant t1 = c1.getResolvedAt() != null ? c1.getResolvedAt() : c1.getSubmittedAt();
                        Instant t2 = c2.getResolvedAt() != null ? c2.getResolvedAt() : c2.getSubmittedAt();
                        if (t1 == null && t2 == null) return 0;
                        if (t1 == null) return 1;
                        if (t2 == null) return -1;
                        return t2.compareTo(t1);
                    })
                    .toList();
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public List<Complaint> findRelatedRecurrences(Integer complaintId) {
        if (complaintId == null) return List.of();
        lock.readLock().lock();
        try {
            Set<Integer> relatedIds = new HashSet<>();
            RecurrenceRecordRepository recRepo = recurrenceRecordRepositoryProvider.getIfAvailable();
            if (recRepo != null) {
                for (RecurrenceRecord rr : recRepo.findAll()) {
                    if (Objects.equals(rr.getOriginalComplaintId(), complaintId) && rr.getRecurringComplaintId() != null) {
                        relatedIds.add(rr.getRecurringComplaintId());
                    }
                    if (Objects.equals(rr.getRecurringComplaintId(), complaintId) && rr.getOriginalComplaintId() != null) {
                        relatedIds.add(rr.getOriginalComplaintId());
                    }
                }
            }

            return complaints.values().stream()
                    .filter(c -> {
                        resolveReferences(c);
                        if (Objects.equals(c.getParentComplaintId(), complaintId)) return true;
                        return relatedIds.contains(c.getComplaintId());
                    })
                    .sorted((c1, c2) -> {
                        Instant t1 = c1.getSubmittedAt();
                        Instant t2 = c2.getSubmittedAt();
                        if (t1 == null && t2 == null) return 0;
                        if (t1 == null) return 1;
                        if (t2 == null) return -1;
                        return t2.compareTo(t1);
                    })
                    .toList();
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public boolean isImageAccessibleByUser(String relativePath, Integer userId) {
        if (relativePath == null || userId == null) return false;
        lock.readLock().lock();
        try {
            return complaints.values().stream().anyMatch(c ->
                    Objects.equals(c.getUserId(), userId) &&
                    (relativePath.equals(c.getImagePath()) || relativePath.equals(c.getResolutionImagePath()))
            );
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public List<Object[]> countChildrenByParentIds(Collection<Integer> ids) {
        if (ids == null || ids.isEmpty()) return List.of();
        lock.readLock().lock();
        try {
            Map<Integer, Long> counts = new HashMap<>();
            for (Complaint c : complaints.values()) {
                Integer parentId = c.getParentComplaintId();
                if (parentId != null && ids.contains(parentId)) {
                    counts.merge(parentId, 1L, Long::sum);
                }
            }
            List<Object[]> rows = new ArrayList<>();
            for (Map.Entry<Integer, Long> entry : counts.entrySet()) {
                rows.add(new Object[]{entry.getKey(), entry.getValue()});
            }
            return rows;
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public long countByStatusIn(Collection<String> statuses) {
        if (statuses == null || statuses.isEmpty()) return 0;
        lock.readLock().lock();
        try {
            return complaints.values().stream()
                    .filter(c -> c.getStatus() != null && statuses.contains(c.getStatus()))
                    .count();
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public long countByUserUserId(Integer userId) {
        if (userId == null) return 0;
        lock.readLock().lock();
        try {
            return complaints.values().stream()
                    .filter(c -> Objects.equals(c.getUserId(), userId))
                    .count();
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public long countByUserUserIdAndStatus(Integer userId, String status) {
        if (userId == null || status == null) return 0;
        lock.readLock().lock();
        try {
            return complaints.values().stream()
                    .filter(c -> Objects.equals(c.getUserId(), userId) && status.equalsIgnoreCase(c.getStatus()))
                    .count();
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public long countByUserUserIdAndStatusIn(Integer userId, Collection<String> statuses) {
        if (userId == null || statuses == null || statuses.isEmpty()) return 0;
        lock.readLock().lock();
        try {
            return complaints.values().stream()
                    .filter(c -> Objects.equals(c.getUserId(), userId) && c.getStatus() != null && statuses.contains(c.getStatus()))
                    .count();
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public List<Object[]> countStatusDistributionByUser(Integer userId) {
        if (userId == null) return List.of();
        lock.readLock().lock();
        try {
            Map<String, Long> map = new LinkedHashMap<>();
            for (Complaint c : complaints.values()) {
                if (Objects.equals(c.getUserId(), userId) && c.getStatus() != null) {
                    map.merge(c.getStatus(), 1L, Long::sum);
                }
            }
            List<Object[]> rows = new ArrayList<>();
            for (Map.Entry<String, Long> entry : map.entrySet()) {
                rows.add(new Object[]{entry.getKey(), entry.getValue()});
            }
            return rows;
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public List<Object[]> countAllByStatus() {
        lock.readLock().lock();
        try {
            Map<String, Long> map = new LinkedHashMap<>();
            for (Complaint c : complaints.values()) {
                if (c.getStatus() != null) {
                    map.merge(c.getStatus(), 1L, Long::sum);
                }
            }
            List<Object[]> rows = new ArrayList<>();
            for (Map.Entry<String, Long> entry : map.entrySet()) {
                rows.add(new Object[]{entry.getKey(), entry.getValue()});
            }
            return rows;
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public List<Object[]> countAllByCategory() {
        lock.readLock().lock();
        try {
            List<Category> allCategories = categoryRepository.findAll();
            Map<String, Long> counts = new HashMap<>();
            for (Category cat : allCategories) {
                counts.put(cat.getName(), 0L);
            }
            for (Complaint c : complaints.values()) {
                resolveReferences(c);
                if (c.getCategory() != null && c.getCategory().getName() != null) {
                    counts.merge(c.getCategory().getName(), 1L, Long::sum);
                }
            }

            List<Object[]> rows = new ArrayList<>();
            for (Map.Entry<String, Long> entry : counts.entrySet()) {
                rows.add(new Object[]{entry.getKey(), entry.getValue()});
            }
            rows.sort((r1, r2) -> {
                int cmp = Long.compare((Long) r2[1], (Long) r1[1]);
                if (cmp != 0) return cmp;
                return String.valueOf(r1[0]).compareToIgnoreCase(String.valueOf(r2[0]));
            });
            return rows;
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public List<Object[]> countAllByDepartment() {
        lock.readLock().lock();
        try {
            Map<String, Long> counts = new HashMap<>();
            for (Complaint c : complaints.values()) {
                resolveReferences(c);
                String deptName = (c.getDepartment() != null && c.getDepartment().getName() != null)
                        ? c.getDepartment().getName()
                        : "Unassigned";
                counts.merge(deptName, 1L, Long::sum);
            }

            List<Object[]> rows = new ArrayList<>();
            for (Map.Entry<String, Long> entry : counts.entrySet()) {
                rows.add(new Object[]{entry.getKey(), entry.getValue()});
            }
            rows.sort((r1, r2) -> Long.compare((Long) r2[1], (Long) r1[1]));
            return rows;
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public List<Instant> findAllSubmittedTimestampsAsc() {
        lock.readLock().lock();
        try {
            return complaints.values().stream()
                    .map(Complaint::getSubmittedAt)
                    .filter(Objects::nonNull)
                    .sorted()
                    .toList();
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public PagedResult<Complaint> findFiltered(Integer userId, String search, Integer categoryId,
                                               String status, String location, int page, int pageSize) {
        lock.readLock().lock();
        try {
            List<Complaint> filtered = complaints.values().stream()
                    .filter(c -> {
                        resolveReferences(c);

                        if (userId != null && !Objects.equals(c.getUserId(), userId)) {
                            return false;
                        }
                        if (categoryId != null && categoryId > 0 && !Objects.equals(c.getCategoryId(), categoryId)) {
                            return false;
                        }
                        if (status != null && !status.isBlank()) {
                            if ("RESOLVED_OR_CLOSED".equalsIgnoreCase(status)) {
                                if (!Complaint.STATUS_RESOLVED.equals(c.getStatus()) && !Complaint.STATUS_CLOSED.equals(c.getStatus())) {
                                    return false;
                                }
                            } else {
                                if (!status.equalsIgnoreCase(c.getStatus())) {
                                    return false;
                                }
                            }
                        }
                        if (search != null && !search.isBlank()) {
                            String query = search.trim().toLowerCase();
                            boolean matchesText = (c.getTitle() != null && c.getTitle().toLowerCase().contains(query))
                                    || (c.getDescription() != null && c.getDescription().toLowerCase().contains(query))
                                    || (c.getArea() != null && c.getArea().toLowerCase().contains(query))
                                    || (c.getCity() != null && c.getCity().toLowerCase().contains(query))
                                    || (c.getLandmark() != null && c.getLandmark().toLowerCase().contains(query));
                            boolean matchesId = false;
                            if (query.matches("\\d+")) {
                                try {
                                    matchesId = Objects.equals(c.getComplaintId(), Integer.parseInt(query));
                                } catch (NumberFormatException ignored) {}
                            }
                            if (!matchesText && !matchesId) {
                                return false;
                            }
                        }
                        if (location != null && !location.isBlank()) {
                            String locQuery = location.trim().toLowerCase();
                            boolean matchesLoc = (c.getArea() != null && c.getArea().toLowerCase().contains(locQuery))
                                    || (c.getCity() != null && c.getCity().toLowerCase().contains(locQuery))
                                    || (c.getState() != null && c.getState().toLowerCase().contains(locQuery))
                                    || (c.getLandmark() != null && c.getLandmark().toLowerCase().contains(locQuery));
                            if (!matchesLoc) {
                                return false;
                            }
                        }
                        return true;
                    })
                    .sorted((c1, c2) -> {
                        Instant t1 = c1.getSubmittedAt();
                        Instant t2 = c2.getSubmittedAt();
                        int cmp = 0;
                        if (t1 == null && t2 != null) cmp = 1;
                        else if (t1 != null && t2 == null) cmp = -1;
                        else if (t1 != null) cmp = t2.compareTo(t1);

                        if (cmp != 0) return cmp;
                        return Integer.compare(
                                c2.getComplaintId() != null ? c2.getComplaintId() : 0,
                                c1.getComplaintId() != null ? c1.getComplaintId() : 0
                        );
                    })
                    .toList();

            long totalCount = filtered.size();
            int safePage = Math.max(1, page);
            int safeSize = Math.max(1, pageSize);
            int fromIndex = (safePage - 1) * safeSize;
            List<Complaint> paged;
            if (fromIndex >= filtered.size()) {
                paged = List.of();
            } else {
                int toIndex = Math.min(fromIndex + safeSize, filtered.size());
                paged = new ArrayList<>(filtered.subList(fromIndex, toIndex));
            }
            return new PagedResult<>(paged, totalCount);
        } finally {
            lock.readLock().unlock();
        }
    }
}
