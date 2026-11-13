package com.civicsphere.repository;

import com.civicsphere.model.Complaint;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ComplaintRepository extends JpaRepository<Complaint, Integer>, JpaSpecificationExecutor<Complaint> {

    @EntityGraph(attributePaths = {"user", "category", "department", "parentComplaint", "resolvedByUser"})
    Optional<Complaint> findDetailedByComplaintId(Integer complaintId);

    @Query("SELECT c FROM Complaint c " +
           "JOIN FETCH c.category " +
           "LEFT JOIN FETCH c.department " +
           "WHERE c.category.categoryId = :categoryId " +
           "AND c.complaintId <> :excludeId " +
           "AND c.status IN ('RESOLVED', 'CLOSED') " +
           "AND c.submittedAt < :submittedAt " +
           "ORDER BY COALESCE(c.resolvedAt, c.submittedAt) DESC")
    List<Complaint> findEligibleResolvedCandidates(
            @Param("categoryId") Integer categoryId,
            @Param("excludeId") Integer excludeId,
            @Param("submittedAt") Instant submittedAt
    );

    @Query("SELECT DISTINCT c FROM Complaint c " +
           "JOIN FETCH c.user " +
           "JOIN FETCH c.category " +
           "LEFT JOIN FETCH c.department " +
           "LEFT JOIN FETCH c.parentComplaint " +
           "WHERE c.parentComplaint.complaintId = :complaintId " +
           "OR c.complaintId IN (SELECT r.recurringComplaint.complaintId FROM RecurrenceRecord r WHERE r.originalComplaint.complaintId = :complaintId) " +
           "OR c.complaintId IN (SELECT r2.originalComplaint.complaintId FROM RecurrenceRecord r2 WHERE r2.recurringComplaint.complaintId = :complaintId) " +
           "ORDER BY c.submittedAt DESC")
    List<Complaint> findRelatedRecurrences(@Param("complaintId") Integer complaintId);

    @Query("SELECT CASE WHEN COUNT(c) > 0 THEN true ELSE false END " +
           "FROM Complaint c WHERE c.user.userId = :userId " +
           "AND (c.imagePath = :relativePath OR c.resolutionImagePath = :relativePath)")
    boolean isImageAccessibleByUser(@Param("relativePath") String relativePath, @Param("userId") Integer userId);

    @Query("SELECT c.parentComplaint.complaintId, COUNT(c) " +
           "FROM Complaint c WHERE c.parentComplaint.complaintId IN :ids GROUP BY c.parentComplaint.complaintId")
    List<Object[]> countChildrenByParentIds(@Param("ids") Collection<Integer> ids);

    long countByStatusIn(Collection<String> statuses);

    long countByUserUserId(Integer userId);

    long countByUserUserIdAndStatus(Integer userId, String status);

    long countByUserUserIdAndStatusIn(Integer userId, Collection<String> statuses);

    @Query("SELECT c.status, COUNT(c) FROM Complaint c WHERE c.user.userId = :userId GROUP BY c.status")
    List<Object[]> countStatusDistributionByUser(@Param("userId") Integer userId);

    @Query("SELECT c.status, COUNT(c) FROM Complaint c GROUP BY c.status")
    List<Object[]> countAllByStatus();

    @Query("SELECT cat.name, COUNT(c) FROM Category cat LEFT JOIN Complaint c ON c.category = cat " +
           "GROUP BY cat.categoryId, cat.name ORDER BY COUNT(c) DESC, cat.name ASC")
    List<Object[]> countAllByCategory();

    @Query("SELECT COALESCE(d.name, 'Unassigned'), COUNT(c) FROM Complaint c LEFT JOIN c.department d " +
           "GROUP BY d.name ORDER BY COUNT(c) DESC")
    List<Object[]> countAllByDepartment();

    @Query("SELECT c.submittedAt FROM Complaint c ORDER BY c.submittedAt ASC")
    List<Instant> findAllSubmittedTimestampsAsc();
}

