package com.civicsphere.repository;

import com.civicsphere.model.ComplaintHistory;

import java.util.List;
import java.util.Optional;

public interface ComplaintHistoryRepository {

    List<ComplaintHistory> findAll();

    Optional<ComplaintHistory> findById(Integer historyId);

    ComplaintHistory save(ComplaintHistory history);

    List<ComplaintHistory> findByComplaintComplaintIdOrderByUpdatedAtAscHistoryIdAsc(Integer complaintId);

    List<ComplaintHistory> findTop12ByComplaintUserUserIdOrderByUpdatedAtDescHistoryIdDesc(Integer userId);

    long count();
}
