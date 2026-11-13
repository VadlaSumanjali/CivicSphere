package com.civicsphere.repository;

import com.civicsphere.model.Department;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface DepartmentRepository extends JpaRepository<Department, Integer> {

    List<Department> findByActiveTrueOrderByNameAsc();

    List<Department> findAllByOrderByNameAsc();

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndDepartmentIdNot(String name, Integer departmentId);

    long countByActiveTrue();

    @Query("SELECT c.department.departmentId, COUNT(c) " +
           "FROM Complaint c WHERE c.department IS NOT NULL GROUP BY c.department.departmentId")
    List<Object[]> countAssignedComplaintsByDepartment();
}

