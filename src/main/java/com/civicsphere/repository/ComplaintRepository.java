package com.civicsphere.repository;

import com.civicsphere.dto.PagedResult;
import com.civicsphere.model.Complaint;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ComplaintRepository {

    List<Complaint> findAll();

    Optional<Complaint> findById(Integer complaintId);

    Complaint save(Complaint complaint);

    long count();

    Optional<Complaint> findDetailedByComplaintId(Integer complaintId);

    List<Complaint> findEligibleResolvedCandidates(Integer categoryId, Integer excludeId, Instant submittedAt);

    List<Complaint> findRelatedRecurrences(Integer complaintId);

    boolean isImageAccessibleByUser(String relativePath, Integer userId);

    List<Object[]> countChildrenByParentIds(Collection<Integer> ids);

    long countByStatusIn(Collection<String> statuses);

    long countByUserUserId(Integer userId);

    long countByUserUserIdAndStatus(Integer userId, String status);

    long countByUserUserIdAndStatusIn(Integer userId, Collection<String> statuses);

    List<Object[]> countStatusDistributionByUser(Integer userId);

    List<Object[]> countAllByStatus();

    List<Object[]> countAllByCategory();

    List<Object[]> countAllByDepartment();

    List<Instant> findAllSubmittedTimestampsAsc();

    PagedResult<Complaint> findFiltered(Integer userId, String search, Integer categoryId,
                                        String status, String location, int page, int pageSize);
}
