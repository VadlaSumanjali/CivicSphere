package com.civicsphere.service;

import com.civicsphere.dto.ComplaintDTO;
import com.civicsphere.dto.PagedResponse;
import com.civicsphere.dto.RecurrenceDTO;
import com.civicsphere.model.Complaint;
import com.civicsphere.model.RecurrenceRecord;
import com.civicsphere.model.User;
import com.civicsphere.repository.ComplaintRepository;
import com.civicsphere.repository.RecurrenceRecordRepository;
import com.civicsphere.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@Service
public class AdminService {

    private static final DateTimeFormatter YEAR_MONTH_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM").withZone(ZoneOffset.UTC);

    private final ComplaintRepository complaintRepository;
    private final RecurrenceRecordRepository recurrenceRepository;
    private final UserRepository userRepository;
    private final ComplaintService complaintService;
    private final RecurrenceService recurrenceService;

    public AdminService(ComplaintRepository complaintRepository,
                        RecurrenceRecordRepository recurrenceRepository,
                        UserRepository userRepository,
                        ComplaintService complaintService,
                        RecurrenceService recurrenceService) {
        this.complaintRepository = complaintRepository;
        this.recurrenceRepository = recurrenceRepository;
        this.userRepository = userRepository;
        this.complaintService = complaintService;
        this.recurrenceService = recurrenceService;
    }

    public Map<String, Object> getDashboardAnalytics() {
        Map<String, Object> data = new LinkedHashMap<>();

        long totalComplaints = complaintRepository.count();
        long pendingComplaints = complaintRepository.countByStatusIn(
                List.of(Complaint.STATUS_SUBMITTED, Complaint.STATUS_UNDER_REVIEW));
        long inProgressComplaints = complaintRepository.countByStatusIn(
                List.of(Complaint.STATUS_ASSIGNED, Complaint.STATUS_IN_PROGRESS));
        long resolvedComplaints = complaintRepository.countByStatusIn(
                List.of(Complaint.STATUS_RESOLVED, Complaint.STATUS_CLOSED));

        data.put("totalComplaints", totalComplaints);
        data.put("pendingComplaints", pendingComplaints);
        data.put("inProgressComplaints", inProgressComplaints);
        data.put("resolvedComplaints", resolvedComplaints);

        long totalCandidates = recurrenceRepository.count();
        long confirmedIssues = recurrenceRepository.countByVerificationStatus(RecurrenceRecord.STATUS_CONFIRMED);
        long openRecurringIssues = recurrenceRepository.countByVerificationStatus(RecurrenceRecord.STATUS_PENDING);

        data.put("totalRecurrenceCandidates", totalCandidates);
        data.put("confirmedRecurringIssues", confirmedIssues);
        data.put("openRecurringIssues", openRecurringIssues);

        data.put("byCategory", toLabelCountList(complaintRepository.countAllByCategory()));
        data.put("byStatus", toLabelCountList(complaintRepository.countAllByStatus()));
        data.put("byDepartment", toLabelCountList(complaintRepository.countAllByDepartment()));

        Map<String, Long> monthlyComplaintsMap = new TreeMap<>();
        for (Instant ts : complaintRepository.findAllSubmittedTimestampsAsc()) {
            if (ts != null) {
                String ym = YEAR_MONTH_FORMAT.format(ts);
                monthlyComplaintsMap.merge(ym, 1L, Long::sum);
            }
        }
        List<Map<String, Object>> monthlyComplaints = new ArrayList<>();
        for (Map.Entry<String, Long> entry : monthlyComplaintsMap.entrySet()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("label", entry.getKey());
            row.put("count", entry.getValue());
            monthlyComplaints.add(row);
        }
        data.put("monthlyComplaints", trimLastMonths(monthlyComplaints, 12));

        List<RecurrenceRecord> allRecurrences = recurrenceRepository.findAll();
        Map<String, long[]> monthlyRecMap = new TreeMap<>();
        for (RecurrenceRecord rr : allRecurrences) {
            if (rr.getCreatedAt() != null) {
                String ym = YEAR_MONTH_FORMAT.format(rr.getCreatedAt());
                long[] counts = monthlyRecMap.computeIfAbsent(ym, k -> new long[2]);
                counts[0]++;
                if (RecurrenceRecord.STATUS_CONFIRMED.equals(rr.getVerificationStatus())) {
                    counts[1]++;
                }
            }
        }
        List<Map<String, Object>> monthlyRecurrences = new ArrayList<>();
        for (Map.Entry<String, long[]> entry : monthlyRecMap.entrySet()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("label", entry.getKey());
            row.put("total", entry.getValue()[0]);
            row.put("confirmed", entry.getValue()[1]);
            monthlyRecurrences.add(row);
        }
        data.put("monthlyRecurrences", trimLastMonths(monthlyRecurrences, 12));

        data.put("totalCitizens", userRepository.countByRole(User.ROLE_CITIZEN));
        PagedResponse<ComplaintDTO> recent = complaintService.listAllComplaints(null, null, null, null, 1, 6);
        data.put("recentComplaints", recent.getItems());

        List<RecurrenceDTO> pendingAlerts = recurrenceService.listRecurrences(RecurrenceRecord.STATUS_PENDING);
        data.put("pendingRecurrenceAlerts",
                pendingAlerts.size() > 6 ? pendingAlerts.subList(0, 6) : pendingAlerts);

        return data;
    }

    private List<Map<String, Object>> toLabelCountList(List<Object[]> rows) {
        List<Map<String, Object>> result = new ArrayList<>(rows.size());
        for (Object[] row : rows) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("label", String.valueOf(row[0]));
            item.put("count", ((Number) row[1]).longValue());
            result.add(item);
        }
        return result;
    }

    private List<Map<String, Object>> trimLastMonths(List<Map<String, Object>> list, int limit) {
        if (list.size() <= limit) {
            return list;
        }
        return list.subList(list.size() - limit, list.size());
    }
}
