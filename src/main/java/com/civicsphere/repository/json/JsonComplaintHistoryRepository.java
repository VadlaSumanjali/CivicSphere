package com.civicsphere.repository.json;

import com.civicsphere.model.Complaint;
import com.civicsphere.model.ComplaintHistory;
import com.civicsphere.repository.ComplaintHistoryRepository;
import com.civicsphere.repository.ComplaintRepository;
import com.civicsphere.repository.UserRepository;
import com.civicsphere.storage.JsonStorage;
import com.fasterxml.jackson.core.type.TypeReference;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@Repository
public class JsonComplaintHistoryRepository implements ComplaintHistoryRepository {

    private static final String FILENAME = "complaint_history.json";

    private final JsonStorage storage;
    private final ComplaintRepository complaintRepository;
    private final UserRepository userRepository;

    private final Map<Integer, ComplaintHistory> historyRecords = new ConcurrentHashMap<>();
    private final AtomicInteger idSeq = new AtomicInteger(0);
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    public JsonComplaintHistoryRepository(JsonStorage storage,
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
            List<ComplaintHistory> loaded = storage.loadList(FILENAME, new TypeReference<List<ComplaintHistory>>() {});
            int maxId = 0;
            for (ComplaintHistory h : loaded) {
                if (h.getHistoryId() != null) {
                    historyRecords.put(h.getHistoryId(), h);
                    if (h.getHistoryId() > maxId) {
                        maxId = h.getHistoryId();
                    }
                }
            }
            idSeq.set(maxId);

            for (ComplaintHistory h : historyRecords.values()) {
                resolveReferences(h);
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    private void resolveReferences(ComplaintHistory h) {
        if (h.getComplaintId() != null && h.getComplaint() == null) {
            h.setComplaint(complaintRepository.findById(h.getComplaintId()).orElse(null));
        }
        if (h.getUpdatedByUserId() != null && h.getUpdatedByUser() == null) {
            h.setUpdatedByUser(userRepository.findById(h.getUpdatedByUserId()).orElse(null));
        }
    }

    @Override
    public List<ComplaintHistory> findAll() {
        lock.readLock().lock();
        try {
            return new ArrayList<>(historyRecords.values());
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public Optional<ComplaintHistory> findById(Integer historyId) {
        if (historyId == null) return Optional.empty();
        lock.readLock().lock();
        try {
            ComplaintHistory h = historyRecords.get(historyId);
            if (h != null) resolveReferences(h);
            return Optional.ofNullable(h);
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public ComplaintHistory save(ComplaintHistory history) {
        lock.writeLock().lock();
        try {
            if (history.getHistoryId() == null || history.getHistoryId() <= 0) {
                history.setHistoryId(idSeq.incrementAndGet());
            } else if (history.getHistoryId() > idSeq.get()) {
                idSeq.set(history.getHistoryId());
            }
            if (history.getUpdatedAt() == null) {
                history.setUpdatedAt(Instant.now());
            }
            if (history.getComplaint() != null) {
                history.setComplaintId(history.getComplaint().getComplaintId());
            }
            if (history.getUpdatedByUser() != null) {
                history.setUpdatedByUserId(history.getUpdatedByUser().getUserId());
            }
            historyRecords.put(history.getHistoryId(), history);
            storage.saveList(FILENAME, new ArrayList<>(historyRecords.values()));
            resolveReferences(history);
            return history;
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public List<ComplaintHistory> findByComplaintComplaintIdOrderByUpdatedAtAscHistoryIdAsc(Integer complaintId) {
        if (complaintId == null) return List.of();
        lock.readLock().lock();
        try {
            return historyRecords.values().stream()
                    .filter(h -> Objects.equals(h.getComplaintId(), complaintId))
                    .peek(this::resolveReferences)
                    .sorted((h1, h2) -> {
                        Instant t1 = h1.getUpdatedAt();
                        Instant t2 = h2.getUpdatedAt();
                        int cmp = 0;
                        if (t1 == null && t2 != null) cmp = -1;
                        else if (t1 != null && t2 == null) cmp = 1;
                        else if (t1 != null) cmp = t1.compareTo(t2);

                        if (cmp != 0) return cmp;
                        return Integer.compare(
                                h1.getHistoryId() != null ? h1.getHistoryId() : 0,
                                h2.getHistoryId() != null ? h2.getHistoryId() : 0
                        );
                    })
                    .toList();
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public List<ComplaintHistory> findTop12ByComplaintUserUserIdOrderByUpdatedAtDescHistoryIdDesc(Integer userId) {
        if (userId == null) return List.of();
        lock.readLock().lock();
        try {
            return historyRecords.values().stream()
                    .peek(this::resolveReferences)
                    .filter(h -> {
                        Complaint c = h.getComplaint();
                        if (c == null && h.getComplaintId() != null) {
                            c = complaintRepository.findById(h.getComplaintId()).orElse(null);
                            h.setComplaint(c);
                        }
                        return c != null && Objects.equals(c.getUserId(), userId);
                    })
                    .sorted((h1, h2) -> {
                        Instant t1 = h1.getUpdatedAt();
                        Instant t2 = h2.getUpdatedAt();
                        int cmp = 0;
                        if (t1 == null && t2 != null) cmp = 1;
                        else if (t1 != null && t2 == null) cmp = -1;
                        else if (t1 != null) cmp = t2.compareTo(t1);

                        if (cmp != 0) return cmp;
                        return Integer.compare(
                                h2.getHistoryId() != null ? h2.getHistoryId() : 0,
                                h1.getHistoryId() != null ? h1.getHistoryId() : 0
                        );
                    })
                    .limit(12)
                    .toList();
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public long count() {
        lock.readLock().lock();
        try {
            return historyRecords.size();
        } finally {
            lock.readLock().unlock();
        }
    }
}
