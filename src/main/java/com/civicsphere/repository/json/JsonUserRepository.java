package com.civicsphere.repository.json;

import com.civicsphere.model.User;
import com.civicsphere.repository.UserRepository;
import com.civicsphere.storage.JsonStorage;
import com.fasterxml.jackson.core.type.TypeReference;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@Repository
public class JsonUserRepository implements UserRepository {

    private static final String FILENAME = "users.json";

    private final JsonStorage storage;
    private final Map<Integer, User> users = new ConcurrentHashMap<>();
    private final AtomicInteger idSeq = new AtomicInteger(0);
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    public JsonUserRepository(JsonStorage storage) {
        this.storage = storage;
    }

    @PostConstruct
    public void init() {
        lock.writeLock().lock();
        try {
            List<User> loaded = storage.loadList(FILENAME, new TypeReference<List<User>>() {});
            int maxId = 0;
            for (User u : loaded) {
                if (u.getUserId() != null) {
                    users.put(u.getUserId(), u);
                    if (u.getUserId() > maxId) {
                        maxId = u.getUserId();
                    }
                }
            }
            idSeq.set(maxId);
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public List<User> findAll() {
        lock.readLock().lock();
        try {
            return new ArrayList<>(users.values());
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public Optional<User> findById(Integer userId) {
        if (userId == null) return Optional.empty();
        lock.readLock().lock();
        try {
            return Optional.ofNullable(users.get(userId));
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public User save(User user) {
        lock.writeLock().lock();
        try {
            if (user.getUserId() == null || user.getUserId() <= 0) {
                user.setUserId(idSeq.incrementAndGet());
            } else if (user.getUserId() > idSeq.get()) {
                idSeq.set(user.getUserId());
            }
            if (user.getCreatedAt() == null) {
                user.setCreatedAt(Instant.now());
            }
            if (user.getRole() == null) {
                user.setRole(User.ROLE_CITIZEN);
            }
            users.put(user.getUserId(), user);
            storage.saveList(FILENAME, new ArrayList<>(users.values()));
            return user;
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public Optional<User> findByEmailIgnoreCase(String email) {
        if (email == null) return Optional.empty();
        String clean = email.trim();
        lock.readLock().lock();
        try {
            return users.values().stream()
                    .filter(u -> u.getEmail() != null && u.getEmail().equalsIgnoreCase(clean))
                    .findFirst();
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public boolean existsByEmailIgnoreCase(String email) {
        if (email == null) return false;
        String clean = email.trim();
        lock.readLock().lock();
        try {
            return users.values().stream()
                    .anyMatch(u -> u.getEmail() != null && u.getEmail().equalsIgnoreCase(clean));
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public long countByRole(String role) {
        if (role == null) return 0;
        lock.readLock().lock();
        try {
            return users.values().stream()
                    .filter(u -> role.equalsIgnoreCase(u.getRole()))
                    .count();
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public long count() {
        lock.readLock().lock();
        try {
            return users.size();
        } finally {
            lock.readLock().unlock();
        }
    }
}
