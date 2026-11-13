-- ============================================================================
-- CivicSphere Database Schema (MySQL 8+)
-- Smart Citizen Complaint Management & Recurring Civic Issue Detection Platform
-- ============================================================================

CREATE DATABASE IF NOT EXISTS civicsphere_db
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

USE civicsphere_db;

-- ----------------------------------------------------------------------------
-- 1. Users
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS users (
    user_id INT AUTO_INCREMENT PRIMARY KEY,
    full_name VARCHAR(120) NOT NULL,
    email VARCHAR(150) NOT NULL,
    phone VARCHAR(20) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    address VARCHAR(255) NOT NULL,
    role ENUM('CITIZEN', 'ADMIN') NOT NULL DEFAULT 'CITIZEN',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_users_email UNIQUE (email),
    INDEX idx_users_role (role)
) ENGINE=InnoDB;

-- ----------------------------------------------------------------------------
-- 2. Categories
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS categories (
    category_id INT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(255) NULL,
    icon_name VARCHAR(50) NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_categories_name UNIQUE (name)
) ENGINE=InnoDB;

-- ----------------------------------------------------------------------------
-- 3. Departments
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS departments (
    department_id INT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(255) NULL,
    contact_email VARCHAR(150) NULL,
    contact_phone VARCHAR(20) NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uq_departments_name UNIQUE (name),
    INDEX idx_departments_active (is_active)
) ENGINE=InnoDB;

-- ----------------------------------------------------------------------------
-- 4. Complaints
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS complaints (
    complaint_id INT AUTO_INCREMENT PRIMARY KEY,
    user_id INT NOT NULL,
    category_id INT NOT NULL,
    department_id INT NULL,
    parent_complaint_id INT NULL,
    title VARCHAR(200) NOT NULL,
    description TEXT NOT NULL,
    area VARCHAR(120) NOT NULL,
    city VARCHAR(100) NOT NULL,
    state VARCHAR(100) NOT NULL,
    landmark VARCHAR(200) NULL,
    latitude DECIMAL(10, 7) NULL,
    longitude DECIMAL(10, 7) NULL,
    image_path VARCHAR(255) NULL,
    resolution_image_path VARCHAR(255) NULL,
    status ENUM('SUBMITTED', 'UNDER_REVIEW', 'ASSIGNED', 'IN_PROGRESS', 'RESOLVED', 'CLOSED') NOT NULL DEFAULT 'SUBMITTED',
    submitted_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    resolved_at TIMESTAMP NULL,
    resolution_remarks TEXT NULL,
    resolved_by INT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_complaints_user FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE RESTRICT,
    CONSTRAINT fk_complaints_category FOREIGN KEY (category_id) REFERENCES categories(category_id) ON DELETE RESTRICT,
    CONSTRAINT fk_complaints_department FOREIGN KEY (department_id) REFERENCES departments(department_id) ON DELETE RESTRICT,
    CONSTRAINT fk_complaints_parent FOREIGN KEY (parent_complaint_id) REFERENCES complaints(complaint_id) ON DELETE SET NULL,
    CONSTRAINT fk_complaints_resolved_by FOREIGN KEY (resolved_by) REFERENCES users(user_id) ON DELETE SET NULL,
    CONSTRAINT chk_complaints_latitude CHECK (latitude IS NULL OR (latitude >= -90.0 AND latitude <= 90.0)),
    CONSTRAINT chk_complaints_longitude CHECK (longitude IS NULL OR (longitude >= -180.0 AND longitude <= 180.0)),
    INDEX idx_complaints_user (user_id),
    INDEX idx_complaints_category_status (category_id, status),
    INDEX idx_complaints_status (status),
    INDEX idx_complaints_location (city, area),
    INDEX idx_complaints_department (department_id),
    INDEX idx_complaints_submitted_at (submitted_at)
) ENGINE=InnoDB;

-- ----------------------------------------------------------------------------
-- 5. Complaint History
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS complaint_history (
    history_id INT AUTO_INCREMENT PRIMARY KEY,
    complaint_id INT NOT NULL,
    previous_status VARCHAR(30) NULL,
    new_status VARCHAR(30) NOT NULL,
    remarks TEXT NULL,
    updated_by INT NOT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_history_complaint FOREIGN KEY (complaint_id) REFERENCES complaints(complaint_id) ON DELETE CASCADE,
    CONSTRAINT fk_history_user FOREIGN KEY (updated_by) REFERENCES users(user_id) ON DELETE RESTRICT,
    INDEX idx_history_complaint (complaint_id, updated_at)
) ENGINE=InnoDB;

-- ----------------------------------------------------------------------------
-- 6. Recurrence Records
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS recurrence_records (
    recurrence_id INT AUTO_INCREMENT PRIMARY KEY,
    original_complaint_id INT NOT NULL,
    recurring_complaint_id INT NOT NULL,
    matching_score INT NOT NULL,
    matching_reason TEXT NOT NULL,
    verification_status ENUM('PENDING', 'CONFIRMED', 'REJECTED') NOT NULL DEFAULT 'PENDING',
    verified_by INT NULL,
    verified_at TIMESTAMP NULL,
    verification_remarks TEXT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_recurrence_original FOREIGN KEY (original_complaint_id) REFERENCES complaints(complaint_id) ON DELETE CASCADE,
    CONSTRAINT fk_recurrence_recurring FOREIGN KEY (recurring_complaint_id) REFERENCES complaints(complaint_id) ON DELETE CASCADE,
    CONSTRAINT fk_recurrence_verified_by FOREIGN KEY (verified_by) REFERENCES users(user_id) ON DELETE SET NULL,
    CONSTRAINT uq_recurrence_pair UNIQUE (original_complaint_id, recurring_complaint_id),
    CONSTRAINT chk_recurrence_score CHECK (matching_score BETWEEN 0 AND 100),
    CONSTRAINT chk_recurrence_distinct CHECK (original_complaint_id <> recurring_complaint_id),
    INDEX idx_recurrence_status (verification_status),
    INDEX idx_recurrence_recurring (recurring_complaint_id)
) ENGINE=InnoDB;

-- ----------------------------------------------------------------------------
-- Default Categories
-- ----------------------------------------------------------------------------
INSERT IGNORE INTO categories (name, description, icon_name, is_active) VALUES
('Road Damage & Potholes', 'Potholes, cracked asphalt, sunken road surfaces, and damaged footpaths.', 'construction', TRUE),
('Garbage Accumulation', 'Uncollected municipal waste, overflowing bins, and illegal dumping.', 'trash-2', TRUE),
('Water Leakage', 'Broken water mains, leaking valves, and potable water wastage.', 'droplets', TRUE),
('Drainage & Sewage', 'Blocked storm drains, sewage overflow, and waterlogging issues.', 'waves', TRUE),
('Streetlight Failures', 'Non-functional streetlights, flickering poles, and dark stretches.', 'lightbulb-off', TRUE),
('Public Sanitation', 'Unclean public restrooms, open waste hazards, and vector breeding spots.', 'shield-alert', TRUE),
('Other Civic Infrastructure', 'Damaged signage, park benches, public railings, and miscellaneous civic hazards.', 'wrench', TRUE);

-- ----------------------------------------------------------------------------
-- Default Departments
-- ----------------------------------------------------------------------------
INSERT IGNORE INTO departments (name, description, contact_email, contact_phone, is_active) VALUES
('Road Maintenance', 'Handles road resurfacing, pothole patching, footpaths, and speed breakers.', 'roads@civicsphere.gov.local', '040-23110001', TRUE),
('Water Supply', 'Manages municipal drinking water pipelines, valves, and leak repairs.', 'water@civicsphere.gov.local', '040-23110002', TRUE),
('Sanitation', 'Oversees solid waste collection, street sweeping, and public hygiene.', 'sanitation@civicsphere.gov.local', '040-23110003', TRUE),
('Drainage', 'Maintains stormwater drains, culverts, and underground sewage lines.', 'drainage@civicsphere.gov.local', '040-23110004', TRUE),
('Electricity and Streetlights', 'Repairs public street lighting, feeder pillars, and electrical poles.', 'lighting@civicsphere.gov.local', '040-23110005', TRUE);

