-- ============================================================================
-- CivicSphere Sample Data Script (Optional Test Seed)
-- Note: Use AdminSetupCLI to create administrator and citizen accounts with
-- BCrypt hashes without storing plaintext passwords in SQL.
-- This script seeds realistic historical complaints assuming users exist, or
-- can be run after AdminSetupCLI --seed-demo.
-- ============================================================================

USE civicsphere_db;

-- Verify categories and departments are populated by schema.sql before running.
-- For automated full sample seeding (including demo citizen/admin accounts and
-- historical complaints), run:
--   java -cp target/civicsphere/WEB-INF/classes;... com.civicsphere.util.AdminSetupCLI --seed-demo

