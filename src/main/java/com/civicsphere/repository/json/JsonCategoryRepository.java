package com.civicsphere.repository.json;

import com.civicsphere.model.Category;
import com.civicsphere.repository.CategoryRepository;
import com.civicsphere.storage.JsonStorage;
import com.fasterxml.jackson.core.type.TypeReference;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@Repository
public class JsonCategoryRepository implements CategoryRepository {

    private static final String FILENAME = "categories.json";

    private final JsonStorage storage;
    private final Map<Integer, Category> categories = new ConcurrentHashMap<>();
    private final AtomicInteger idSeq = new AtomicInteger(0);
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    public JsonCategoryRepository(JsonStorage storage) {
        this.storage = storage;
    }

    @PostConstruct
    public void init() {
        lock.writeLock().lock();
        try {
            List<Category> loaded = storage.loadList(FILENAME, new TypeReference<List<Category>>() {});
            int maxId = 0;
            for (Category c : loaded) {
                if (c.getCategoryId() != null) {
                    categories.put(c.getCategoryId(), c);
                    if (c.getCategoryId() > maxId) {
                        maxId = c.getCategoryId();
                    }
                }
            }
            idSeq.set(maxId);
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public List<Category> findAll() {
        lock.readLock().lock();
        try {
            return new ArrayList<>(categories.values());
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public Optional<Category> findById(Integer categoryId) {
        if (categoryId == null) return Optional.empty();
        lock.readLock().lock();
        try {
            return Optional.ofNullable(categories.get(categoryId));
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public Category save(Category category) {
        lock.writeLock().lock();
        try {
            if (category.getCategoryId() == null || category.getCategoryId() <= 0) {
                category.setCategoryId(idSeq.incrementAndGet());
            } else if (category.getCategoryId() > idSeq.get()) {
                idSeq.set(category.getCategoryId());
            }
            if (category.getCreatedAt() == null) {
                category.setCreatedAt(Instant.now());
            }
            if (category.getIconName() == null) {
                category.setIconName("alert-circle");
            }
            categories.put(category.getCategoryId(), category);
            storage.saveList(FILENAME, new ArrayList<>(categories.values()));
            return category;
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public List<Category> findByActiveTrueOrderByNameAsc() {
        lock.readLock().lock();
        try {
            return categories.values().stream()
                    .filter(Category::isActive)
                    .sorted(Comparator.comparing(Category::getName, String.CASE_INSENSITIVE_ORDER))
                    .toList();
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public long count() {
        lock.readLock().lock();
        try {
            return categories.size();
        } finally {
            lock.readLock().unlock();
        }
    }
}
