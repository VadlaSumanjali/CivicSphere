package com.civicsphere.repository;

import com.civicsphere.model.ComplaintHistory;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ComplaintHistoryRepository extends JpaRepository<ComplaintHistory, Integer> {

    @EntityGraph(attributePaths = {"updatedByUser"})
    List<ComplaintHistory> findByComplaintComplaintIdOrderByUpdatedAtAscHistoryIdAsc(Integer complaintId);

    @EntityGraph(attributePaths = {"complaint", "updatedByUser"})
    List<ComplaintHistory> findTop12ByComplaintUserUserIdOrderByUpdatedAtDescHistoryIdDesc(Integer userId);
}

