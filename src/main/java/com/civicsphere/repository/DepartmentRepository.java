package com.civicsphere.repository;

import com.civicsphere.model.Department;

import java.util.List;
import java.util.Optional;

public interface DepartmentRepository {

    List<Department> findAll();

    Optional<Department> findById(Integer departmentId);

    Department save(Department department);

    List<Department> findByActiveTrueOrderByNameAsc();

    List<Department> findAllByOrderByNameAsc();

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndDepartmentIdNot(String name, Integer departmentId);

    long countByActiveTrue();

    long count();

    List<Object[]> countAssignedComplaintsByDepartment();
}
