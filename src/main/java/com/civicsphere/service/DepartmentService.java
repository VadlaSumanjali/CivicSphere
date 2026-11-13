package com.civicsphere.service;

import com.civicsphere.dto.DepartmentDTO;
import com.civicsphere.model.Department;
import com.civicsphere.repository.DepartmentRepository;
import com.civicsphere.util.ValidationUtil;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class DepartmentService {

    private final DepartmentRepository departmentRepository;

    public DepartmentService(DepartmentRepository departmentRepository) {
        this.departmentRepository = departmentRepository;
    }

    public List<DepartmentDTO> listDepartments(boolean activeOnly) {
        List<Department> departments = activeOnly
                ? departmentRepository.findByActiveTrueOrderByNameAsc()
                : departmentRepository.findAllByOrderByNameAsc();

        Map<Integer, Long> counts = new HashMap<>();
        for (Object[] row : departmentRepository.countAssignedComplaintsByDepartment()) {
            Integer deptId = (Integer) row[0];
            Long count = (Long) row[1];
            counts.put(deptId, count);
        }

        return departments.stream()
                .map(d -> DepartmentDTO.fromEntity(d, counts.getOrDefault(d.getDepartmentId(), 0L)))
                .toList();
    }

    public DepartmentDTO createDepartment(String name, String description, String contactEmail, String contactPhone) {
        String cleanName = ValidationUtil.clean(name);
        String cleanDesc = ValidationUtil.clean(description);
        String cleanEmail = ValidationUtil.clean(contactEmail);
        String cleanPhone = ValidationUtil.clean(contactPhone);

        validateDepartmentFields(cleanName, cleanEmail, cleanPhone);

        if (departmentRepository.existsByNameIgnoreCase(cleanName)) {
            throw new ServiceException(409, "A department with this name already exists.");
        }

        Department dept = new Department();
        dept.setName(cleanName);
        dept.setDescription(cleanDesc);
        dept.setContactEmail(cleanEmail);
        dept.setContactPhone(cleanPhone);
        dept.setActive(true);
        Department saved = departmentRepository.save(dept);
        return DepartmentDTO.fromEntity(saved, 0L);
    }

    public DepartmentDTO updateDepartment(Integer departmentId, String name, String description,
                                          String contactEmail, String contactPhone, Boolean active) {
        String cleanName = ValidationUtil.clean(name);
        String cleanDesc = ValidationUtil.clean(description);
        String cleanEmail = ValidationUtil.clean(contactEmail);
        String cleanPhone = ValidationUtil.clean(contactPhone);

        validateDepartmentFields(cleanName, cleanEmail, cleanPhone);

        Department existing = departmentRepository.findById(departmentId)
                .orElseThrow(() -> new ServiceException(404, "Department not found."));

        if (departmentRepository.existsByNameIgnoreCaseAndDepartmentIdNot(cleanName, departmentId)) {
            throw new ServiceException(409, "Another department with this name already exists.");
        }

        existing.setName(cleanName);
        existing.setDescription(cleanDesc);
        existing.setContactEmail(cleanEmail);
        existing.setContactPhone(cleanPhone);
        if (active != null) {
            existing.setActive(active);
        }
        Department saved = departmentRepository.save(existing);

        long count = departmentRepository.countAssignedComplaintsByDepartment().stream()
                .filter(row -> departmentId.equals(row[0]))
                .map(row -> (Long) row[1])
                .findFirst()
                .orElse(0L);
        return DepartmentDTO.fromEntity(saved, count);
    }

    public DepartmentDTO deactivateDepartment(Integer departmentId) {
        Department existing = departmentRepository.findById(departmentId)
                .orElseThrow(() -> new ServiceException(404, "Department not found."));
        existing.setActive(false);
        Department saved = departmentRepository.save(existing);

        long count = departmentRepository.countAssignedComplaintsByDepartment().stream()
                .filter(row -> departmentId.equals(row[0]))
                .map(row -> (Long) row[1])
                .findFirst()
                .orElse(0L);
        return DepartmentDTO.fromEntity(saved, count);
    }

    private void validateDepartmentFields(String cleanName, String cleanEmail, String cleanPhone) {
        if (ValidationUtil.isBlank(cleanName) || cleanName.length() < 2 || cleanName.length() > 100) {
            throw new ServiceException(400, "Department name must be between 2 and 100 characters.");
        }
        if (!ValidationUtil.isBlank(cleanEmail) && !ValidationUtil.isValidEmail(cleanEmail)) {
            throw new ServiceException(400, "Department contact email is invalid.");
        }
        if (!ValidationUtil.isBlank(cleanPhone) && !ValidationUtil.isValidPhone(cleanPhone)) {
            throw new ServiceException(400, "Department contact phone is invalid.");
        }
    }
}
