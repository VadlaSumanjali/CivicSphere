package com.civicsphere.repository.json;

import com.civicsphere.model.Complaint;
import com.civicsphere.model.RecurrenceRecord;
import com.civicsphere.repository.ComplaintRepository;
import com.civicsphere.repository.RecurrenceRecordRepository;
import com.civicsphere.repository.UserRepository;
import com.civicsphere.storage.JsonStorage;
import com.fasterxml.jackson.core.type.TypeReference;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

@Repository
public class JsonRecurrenceRecordRepository implements RecurrenceRecordRepository {

    private static final String FILENAME = "recurrence_records.json";

    private final JsonStorage storage;
    private final ComplaintRepository complaintRepository;
    private final UserRepository userRepository;

    private final Map<Integer, RecurrenceRecord> records = new ConcurrentHashMap<>();
    private final AtomicInteger idSeq = new AtomicInteger(0);
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    public JsonRecurrenceRecordRepository(JsonStorage storage,
                                          ComplaintRepository complaintRepository,
                                          UserRepository userRepository) {
        this.storage = storage;
        this.complaintRepository = complaintRepository;
        this.userRepository = userRepository;
    }

    @PostConstruct
    public void init() {
        lock.writeLock().lock();
        try {
            List<RecurrenceRecord> loaded = storage.loadList(FILENAME, new TypeReference<List<RecurrenceRecord>>() {});
            int maxId = 0;
            for (RecurrenceRecord r : loaded) {
                if (r.getRecurrenceId() != null) {
                    records.put(r.getRecurrenceId(), r);
                    if (r.getRecurrenceId() > maxId) {
                        maxId = r.getRecurrenceId();
                    }
                }
            }
            idSeq.set(maxId);

            for (RecurrenceRecord r : records.values()) {
                resolveReferences(r);
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    private void resolveReferences(RecurrenceRecord r) {
        if (r.getOriginalComplaintId() != null && r.getOriginalComplaint() == null) {
            r.setOriginalComplaint(complaintRepository.findById(r.getOriginalComplaintId()).orElse(null));
        }
        if (r.getRecurringComplaintId() != null && r.getRecurringComplaint() == null) {
            r.setRecurringComplaint(complaintRepository.findById(r.getRecurringComplaintId()).orElse(null));
        }
        if (r.getVerifiedByUserId() != null && r.getVerifiedByUser() == null) {
            r.setVerifiedByUser(userRepository.findById(r.getVerifiedByUserId()).orElse(null));
        }
    }

    @Override
    public List<RecurrenceRecord> findAll() {
        lock.readLock().lock();
        try {
            return records.values().stream().peek(this::resolveReferences).toList();
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public Optional<RecurrenceRecord> findById(Integer recurrenceId) {
        if (recurrenceId == null) return Optional.empty();
        lock.readLock().lock();
        try {
            RecurrenceRecord r = records.get(recurrenceId);
            if (r != null) resolveReferences(r);
            return Optional.ofNullable(r);
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public RecurrenceRecord save(RecurrenceRecord record) {
        lock.writeLock().lock();
        try {
            if (record.getRecurrenceId() == null || record.getRecurrenceId() <= 0) {
                record.setRecurrenceId(idSeq.incrementAndGet());
            } else if (record.getRecurrenceId() > idSeq.get()) {
                idSeq.set(record.getRecurrenceId());
            }
            if (record.getCreatedAt() == null) {
                record.setCreatedAt(Instant.now());
            }
            if (record.getVerificationStatus() == null) {
                record.setVerificationStatus(RecurrenceRecord.STATUS_PENDING);
            }
            if (record.getOriginalComplaint() != null) {
                record.setOriginalComplaintId(record.getOriginalComplaint().getComplaintId());
            }
            if (record.getRecurringComplaint() != null) {
                record.setRecurringComplaintId(record.getRecurringComplaint().getComplaintId());
            }
            if (record.getVerifiedByUser() != null) {
                record.setVerifiedByUserId(record.getVerifiedByUser().getUserId());
            }

            records.put(record.getRecurrenceId(), record);
            storage.saveList(FILENAME, new ArrayList<>(records.values()));
            resolveReferences(record);
            return record;
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public long count() {
        lock.readLock().lock();
        try {
            return records.size();
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public boolean existsByOriginalComplaintComplaintIdAndRecurringComplaintComplaintId(
            Integer originalComplaintId, Integer recurringComplaintId) {
        if (originalComplaintId == null || recurringComplaintId == null) return false;
        lock.readLock().lock();
        try {
            return records.values().stream().anyMatch(r ->
                    Objects.equals(r.getOriginalComplaintId(), originalComplaintId) &&
                    Objects.equals(r.getRecurringComplaintId(), recurringComplaintId)
            );
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public long countByVerificationStatusNot(String status) {
        if (status == null) return 0;
        lock.readLock().lock();
        try {
            return records.values().stream()
                    .filter(r -> !status.equalsIgnoreCase(r.getVerificationStatus()))
                    .count();
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public long countByVerificationStatus(String status) {
        if (status == null) return 0;
        lock.readLock().lock();
        try {
            return records.values().stream()
                    .filter(r -> status.equalsIgnoreCase(r.getVerificationStatus()))
                    .count();
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public long countDistinctNonRejectedForCitizen(Integer userId) {
        if (userId == null) return 0;
        lock.readLock().lock();
        try {
            Set<Integer> distinct = records.values().stream()
                    .peek(this::resolveReferences)
                    .filter(r -> !RecurrenceRecord.STATUS_REJECTED.equalsIgnoreCase(r.getVerificationStatus()))
                    .filter(r -> {
                        Complaint rc = r.getRecurringComplaint();
                        if (rc == null && r.getRecurringComplaintId() != null) {
                            rc = complaintRepository.findById(r.getRecurringComplaintId()).orElse(null);
                            r.setRecurringComplaint(rc);
                        }
                        return rc != null && Objects.equals(rc.getUserId(), userId);
                    })
                    .map(RecurrenceRecord::getRecurringComplaintId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
            return distinct.size();
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public boolean existsOtherNonRejectedForRecurring(Integer recurringId, Integer excludeId) {
        if (recurringId == null) return false;
        lock.readLock().lock();
        try {
            return records.values().stream().anyMatch(r ->
                    Objects.equals(r.getRecurringComplaintId(), recurringId) &&
                    !Objects.equals(r.getRecurrenceId(), excludeId) &&
                    !RecurrenceRecord.STATUS_REJECTED.equalsIgnoreCase(r.getVerificationStatus())
            );
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public List<RecurrenceRecord> findAllDetailed(String status) {
        lock.readLock().lock();
        try {
            return records.values().stream()
                    .peek(this::resolveReferences)
                    .filter(r -> status == null || status.isBlank() || status.equalsIgnoreCase(r.getVerificationStatus()))
                    .sorted((r1, r2) -> {
                        int p1 = statusPriority(r1.getVerificationStatus());
                        int p2 = statusPriority(r2.getVerificationStatus());
                        if (p1 != p2) return Integer.compare(p1, p2);

                        Instant t1 = r1.getCreatedAt();
                        Instant t2 = r2.getCreatedAt();
                        if (t1 == null && t2 != null) return 1;
                        if (t1 != null && t2 == null) return -1;
                        if (t1 != null) return t2.compareTo(t1);
                        return Integer.compare(
                                r2.getRecurrenceId() != null ? r2.getRecurrenceId() : 0,
                                r1.getRecurrenceId() != null ? r1.getRecurrenceId() : 0
                        );
                    })
                    .toList();
        } finally {
            lock.readLock().unlock();
        }
    }

    private int statusPriority(String status) {
        if (RecurrenceRecord.STATUS_PENDING.equalsIgnoreCase(status)) return 0;
        if (RecurrenceRecord.STATUS_CONFIRMED.equalsIgnoreCase(status)) return 1;
        return 2;
    }

    @Override
    public Optional<RecurrenceRecord> findDetailedById(Integer id) {
        return findById(id);
    }

    @Override
    public List<RecurrenceRecord> findDetailedByComplaintInvolved(Integer complaintId) {
        if (complaintId == null) return List.of();
        lock.readLock().lock();
        try {
            return records.values().stream()
                    .peek(this::resolveReferences)
                    .filter(r -> Objects.equals(r.getOriginalComplaintId(), complaintId) ||
                                 Objects.equals(r.getRecurringComplaintId(), complaintId))
                    .sorted((r1, r2) -> {
                        int scoreCmp = Integer.compare(r2.getMatchingScore(), r1.getMatchingScore());
                        if (scoreCmp != 0) return scoreCmp;

                        Instant t1 = r1.getCreatedAt();
                        Instant t2 = r2.getCreatedAt();
                        if (t1 == null && t2 != null) return 1;
                        if (t1 != null && t2 == null) return -1;
                        if (t1 != null) return t2.compareTo(t1);
                        return 0;
                    })
                    .toList();
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public List<RecurrenceRecord> findDetailedCitizenAlerts(Integer userId) {
        if (userId == null) return List.of();
        lock.readLock().lock();
        try {
            return records.values().stream()
                    .peek(this::resolveReferences)
                    .filter(r -> !RecurrenceRecord.STATUS_REJECTED.equalsIgnoreCase(r.getVerificationStatus()))
                    .filter(r -> {
                        Complaint rc = r.getRecurringComplaint();
                        if (rc == null && r.getRecurringComplaintId() != null) {
                            rc = complaintRepository.findById(r.getRecurringComplaintId()).orElse(null);
                            r.setRecurringComplaint(rc);
                        }
                        return rc != null && Objects.equals(rc.getUserId(), userId);
                    })
                    .sorted((r1, r2) -> {
                        Instant t1 = r1.getCreatedAt();
                        Instant t2 = r2.getCreatedAt();
                        if (t1 == null && t2 != null) return 1;
                        if (t1 != null && t2 == null) return -1;
                        if (t1 != null) return t2.compareTo(t1);
                        return 0;
                    })
                    .toList();
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public List<RecurrenceRecord> findNonRejectedByRecurringIds(Collection<Integer> ids) {
        if (ids == null || ids.isEmpty()) return List.of();
        lock.readLock().lock();
        try {
            return records.values().stream()
                    .peek(this::resolveReferences)
                    .filter(r -> ids.contains(r.getRecurringComplaintId()) &&
                            !RecurrenceRecord.STATUS_REJECTED.equalsIgnoreCase(r.getVerificationStatus()))
                    .sorted((r1, r2) -> {
                        int scoreCmp = Integer.compare(r2.getMatchingScore(), r1.getMatchingScore());
                        if (scoreCmp != 0) return scoreCmp;

                        Instant t1 = r1.getCreatedAt();
                        Instant t2 = r2.getCreatedAt();
                        if (t1 == null && t2 != null) return 1;
                        if (t1 != null && t2 == null) return -1;
                        if (t1 != null) return t2.compareTo(t1);
                        return 0;
                    })
                    .toList();
        } finally {
            lock.readLock().unlock();
        }
    }
}
