package com.civicsphere.repository;

import com.civicsphere.model.RecurrenceRecord;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface RecurrenceRecordRepository {

    List<RecurrenceRecord> findAll();

    Optional<RecurrenceRecord> findById(Integer recurrenceId);

    RecurrenceRecord save(RecurrenceRecord record);

    long count();

    boolean existsByOriginalComplaintComplaintIdAndRecurringComplaintComplaintId(
            Integer originalComplaintId, Integer recurringComplaintId);

    long countByVerificationStatusNot(String status);

    long countByVerificationStatus(String status);

    long countDistinctNonRejectedForCitizen(Integer userId);

    boolean existsOtherNonRejectedForRecurring(Integer recurringId, Integer excludeId);

    List<RecurrenceRecord> findAllDetailed(String status);

    Optional<RecurrenceRecord> findDetailedById(Integer id);

    List<RecurrenceRecord> findDetailedByComplaintInvolved(Integer complaintId);

    List<RecurrenceRecord> findDetailedCitizenAlerts(Integer userId);

    List<RecurrenceRecord> findNonRejectedByRecurringIds(Collection<Integer> ids);
}
