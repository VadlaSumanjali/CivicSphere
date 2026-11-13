package com.civicsphere.repository;

import com.civicsphere.model.RecurrenceRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface RecurrenceRecordRepository extends JpaRepository<RecurrenceRecord, Integer> {

    boolean existsByOriginalComplaintComplaintIdAndRecurringComplaintComplaintId(
            Integer originalComplaintId, Integer recurringComplaintId);

    long countByVerificationStatusNot(String status);

    long countByVerificationStatus(String status);

    @Query("SELECT COUNT(DISTINCT r.recurringComplaint.complaintId) " +
           "FROM RecurrenceRecord r WHERE r.recurringComplaint.user.userId = :userId " +
           "AND r.verificationStatus <> 'REJECTED'")
    long countDistinctNonRejectedForCitizen(@Param("userId") Integer userId);

    @Query("SELECT CASE WHEN COUNT(r) > 0 THEN true ELSE false END " +
           "FROM RecurrenceRecord r WHERE r.recurringComplaint.complaintId = :recurringId " +
           "AND r.verificationStatus <> 'REJECTED' AND r.recurrenceId <> :excludeId")
    boolean existsOtherNonRejectedForRecurring(
            @Param("recurringId") Integer recurringId, @Param("excludeId") Integer excludeId);

    @Query("SELECT r FROM RecurrenceRecord r " +
           "JOIN FETCH r.originalComplaint o " +
           "LEFT JOIN FETCH o.department " +
           "JOIN FETCH r.recurringComplaint rc " +
           "JOIN FETCH rc.category " +
           "JOIN FETCH rc.user " +
           "LEFT JOIN FETCH r.verifiedByUser " +
           "WHERE (:status IS NULL OR r.verificationStatus = :status) " +
           "ORDER BY CASE r.verificationStatus WHEN 'PENDING' THEN 0 WHEN 'CONFIRMED' THEN 1 ELSE 2 END, r.createdAt DESC")
    List<RecurrenceRecord> findAllDetailed(@Param("status") String status);

    @Query("SELECT r FROM RecurrenceRecord r " +
           "JOIN FETCH r.originalComplaint o " +
           "LEFT JOIN FETCH o.department " +
           "JOIN FETCH r.recurringComplaint rc " +
           "JOIN FETCH rc.category " +
           "JOIN FETCH rc.user " +
           "LEFT JOIN FETCH r.verifiedByUser " +
           "WHERE r.recurrenceId = :id")
    Optional<RecurrenceRecord> findDetailedById(@Param("id") Integer id);

    @Query("SELECT r FROM RecurrenceRecord r " +
           "JOIN FETCH r.originalComplaint o " +
           "LEFT JOIN FETCH o.department " +
           "JOIN FETCH r.recurringComplaint rc " +
           "JOIN FETCH rc.category " +
           "JOIN FETCH rc.user " +
           "LEFT JOIN FETCH r.verifiedByUser " +
           "WHERE o.complaintId = :complaintId OR rc.complaintId = :complaintId " +
           "ORDER BY r.matchingScore DESC, r.createdAt DESC")
    List<RecurrenceRecord> findDetailedByComplaintInvolved(@Param("complaintId") Integer complaintId);

    @Query("SELECT r FROM RecurrenceRecord r " +
           "JOIN FETCH r.originalComplaint o " +
           "LEFT JOIN FETCH o.department " +
           "JOIN FETCH r.recurringComplaint rc " +
           "JOIN FETCH rc.category " +
           "JOIN FETCH rc.user " +
           "LEFT JOIN FETCH r.verifiedByUser " +
           "WHERE rc.user.userId = :userId AND r.verificationStatus <> 'REJECTED' " +
           "ORDER BY r.createdAt DESC")
    List<RecurrenceRecord> findDetailedCitizenAlerts(@Param("userId") Integer userId);

    @Query("SELECT r FROM RecurrenceRecord r " +
           "WHERE r.recurringComplaint.complaintId IN :ids AND r.verificationStatus <> 'REJECTED' " +
           "ORDER BY r.matchingScore DESC, r.createdAt DESC")
    List<RecurrenceRecord> findNonRejectedByRecurringIds(@Param("ids") Collection<Integer> ids);
}

