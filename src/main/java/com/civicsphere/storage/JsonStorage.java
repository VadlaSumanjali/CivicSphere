package com.civicsphere.storage;

import com.civicsphere.config.AppProperties;
import com.civicsphere.model.Category;
import com.civicsphere.model.Department;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@Component
public class JsonStorage {

    private static final Logger log = LoggerFactory.getLogger(JsonStorage.class);

    private final Path dataDir;
    private final ObjectMapper objectMapper;
    private final ReentrantReadWriteLock fileLock = new ReentrantReadWriteLock();

    public JsonStorage(AppProperties appProperties, ObjectMapper objectMapper) {
        this.dataDir = Paths.get(appProperties.getDataDir()).toAbsolutePath().normalize();
        this.objectMapper = objectMapper;
        initDirectory();
    }

    private void initDirectory() {
        try {
            if (!Files.exists(dataDir)) {
                Files.createDirectories(dataDir);
                log.info("Initialized CivicSphere data directory: {}", dataDir);
            }
            initDefaultCategories();
            initDefaultDepartments();
        } catch (IOException e) {
            log.error("Failed to initialize CivicSphere data directory: {}", dataDir, e);
            throw new RuntimeException("Could not initialize storage directory " + dataDir, e);
        }
    }

    private void initDefaultCategories() throws IOException {
        Path catFile = dataDir.resolve("categories.json");
        if (!Files.exists(catFile)) {
            List<Category> defaults = List.of(
                    new Category(1, "Road Damage & Potholes", "Potholes, cracked asphalt, sunken road surfaces, and damaged footpaths.", "construction", true),
                    new Category(2, "Garbage Accumulation", "Uncollected municipal waste, overflowing bins, and illegal dumping.", "trash-2", true),
                    new Category(3, "Water Leakage", "Broken water mains, leaking valves, and potable water wastage.", "droplets", true),
                    new Category(4, "Drainage & Sewage", "Blocked storm drains, sewage overflow, and waterlogging issues.", "waves", true),
                    new Category(5, "Streetlight Failures", "Non-functional streetlights, flickering poles, and dark stretches.", "lightbulb-off", true),
                    new Category(6, "Public Sanitation", "Unclean public restrooms, open waste hazards, and vector breeding spots.", "shield-alert", true),
                    new Category(7, "Other Civic Infrastructure", "Damaged signage, park benches, public railings, and miscellaneous civic hazards.", "wrench", true)
            );
            writeAtomically(catFile, defaults);
            log.info("Initialized default categories in {}", catFile);
        }
    }

    private void initDefaultDepartments() throws IOException {
        Path deptFile = dataDir.resolve("departments.json");
        if (!Files.exists(deptFile)) {
            List<Department> defaults = List.of(
                    new Department(1, "Road Maintenance", "Handles road resurfacing, pothole patching, footpaths, and speed breakers.", "roads@civicsphere.gov.local", "040-23110001", true),
                    new Department(2, "Water Supply", "Manages municipal drinking water pipelines, valves, and leak repairs.", "water@civicsphere.gov.local", "040-23110002", true),
                    new Department(3, "Sanitation", "Oversees solid waste collection, street sweeping, and public hygiene.", "sanitation@civicsphere.gov.local", "040-23110003", true),
                    new Department(4, "Drainage", "Maintains stormwater drains, culverts, and underground sewage lines.", "drainage@civicsphere.gov.local", "040-23110004", true),
                    new Department(5, "Electricity and Streetlights", "Repairs public street lighting, feeder pillars, and electrical poles.", "lighting@civicsphere.gov.local", "040-23110005", true)
            );
            writeAtomically(deptFile, defaults);
            log.info("Initialized default departments in {}", deptFile);
        }
    }

    public <T> List<T> loadList(String filename, TypeReference<List<T>> typeReference) {
        fileLock.readLock().lock();
        try {
            Path file = dataDir.resolve(filename);
            if (!Files.exists(file)) {
                return new ArrayList<>();
            }
            byte[] bytes = Files.readAllBytes(file);
            if (bytes.length == 0) {
                return new ArrayList<>();
            }
            return objectMapper.readValue(bytes, typeReference);
        } catch (IOException e) {
            log.error("Failed to load list from {}", filename, e);
            throw new RuntimeException("Error reading " + filename, e);
        } finally {
            fileLock.readLock().unlock();
        }
    }

    public <T> void saveList(String filename, List<T> items) {
        fileLock.writeLock().lock();
        try {
            Path targetFile = dataDir.resolve(filename);
            writeAtomically(targetFile, items);
        } catch (IOException e) {
            log.error("Failed to save list to {}", filename, e);
            throw new RuntimeException("Error writing " + filename, e);
        } finally {
            fileLock.writeLock().unlock();
        }
    }

    private <T> void writeAtomically(Path targetFile, List<T> items) throws IOException {
        Path parent = targetFile.getParent();
        if (parent != null && !Files.exists(parent)) {
            Files.createDirectories(parent);
        }
        byte[] bytes = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(items);
        Path tempFile = null;
        try {
            tempFile = Files.createTempFile(parent, "store-", ".tmp");
            Files.write(tempFile, bytes);
            try {
                Files.move(tempFile, targetFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (Exception moveEx) {
                try {
                    Files.move(tempFile, targetFile, StandardCopyOption.REPLACE_EXISTING);
                } catch (Exception fallbackEx) {
                    Files.write(targetFile, bytes);
                }
            }
        } catch (Exception e) {
            // Ultimate fallback: write directly to targetFile
            Files.write(targetFile, bytes);
        } finally {
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (Exception ignored) {}
            }
        }
    }

    public Path getDataDir() {
        return dataDir;
    }
}
