package com.civicsphere.service;

import com.civicsphere.config.AppProperties;
import com.civicsphere.dto.RecurrenceDTO;
import com.civicsphere.model.Complaint;
import com.civicsphere.model.ComplaintHistory;
import com.civicsphere.model.RecurrenceRecord;
import com.civicsphere.model.User;
import com.civicsphere.repository.ComplaintHistoryRepository;
import com.civicsphere.repository.ComplaintRepository;
import com.civicsphere.repository.RecurrenceRecordRepository;
import com.civicsphere.repository.UserRepository;
import com.civicsphere.util.LocationUtil;
import com.civicsphere.util.TextSimilarityUtil;
import com.civicsphere.util.ValidationUtil;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Rule-based Recurring Civic Issue Detection Engine.
 *
 * Scoring breakdown (max 100):
 * - Same category match:                       +30 points (required)
 * - Location match (GPS <= threshold / area):  up to +40 points
 * - Title + description keyword similarity:    up to +20 points
 * - Historical complaint already RESOLVED/CLOSED: +10 points
 *
 * Threshold: total score >= 70 with meaningful location & description overlap.
 */
@Service
public class RecurrenceService {

    private final ComplaintRepository complaintRepository;
    private final RecurrenceRecordRepository recurrenceRepository;
    private final ComplaintHistoryRepository historyRepository;
    private final UserRepository userRepository;
    private final AppProperties appProperties;

    public static final class EvaluationResult {
        private final boolean eligible;
        private final int score;
        private final String reason;

        public EvaluationResult(boolean eligible, int score, String reason) {
            this.eligible = eligible;
            this.score = score;
            this.reason = reason;
        }

        public boolean isEligible() {
            return eligible;
        }

        public int getScore() {
            return score;
        }

        public String getReason() {
            return reason;
        }
    }

    public RecurrenceService(ComplaintRepository complaintRepository,
                             RecurrenceRecordRepository recurrenceRepository,
                             ComplaintHistoryRepository historyRepository,
                             UserRepository userRepository,
                             AppProperties appProperties) {
        this.complaintRepository = complaintRepository;
        this.recurrenceRepository = recurrenceRepository;
        this.historyRepository = historyRepository;
        this.userRepository = userRepository;
        this.appProperties = appProperties != null ? appProperties : new AppProperties();
    }

    public List<RecurrenceRecord> detectAndRecordRecurrences(Complaint newComplaint, User actor) {
        if (newComplaint == null || newComplaint.getComplaintId() == null || newComplaint.getCategory() == null) {
            return List.of();
        }
        Instant submittedAt = newComplaint.getSubmittedAt() != null ? newComplaint.getSubmittedAt() : Instant.now();
        List<Complaint> candidates = complaintRepository.findEligibleResolvedCandidates(
                newComplaint.getCategory().getCategoryId(),
                newComplaint.getComplaintId(),
                submittedAt
        );

        List<RecurrenceRecord> createdRecords = new ArrayList<>();
        Complaint bestOriginal = null;
        int bestScore = 0;

        for (Complaint historical : candidates) {
            if (recurrenceRepository.existsByOriginalComplaintComplaintIdAndRecurringComplaintComplaintId(
                    historical.getComplaintId(), newComplaint.getComplaintId())) {
                continue;
            }
            EvaluationResult eval = evaluateCandidate(historical, newComplaint);
            if (!eval.isEligible()) {
                continue;
            }

            RecurrenceRecord record = new RecurrenceRecord();
            record.setOriginalComplaint(historical);
            record.setRecurringComplaint(newComplaint);
            record.setMatchingScore(eval.getScore());
            record.setMatchingReason(eval.getReason());
            record.setVerificationStatus(RecurrenceRecord.STATUS_PENDING);
            RecurrenceRecord saved = recurrenceRepository.save(record);
            createdRecords.add(saved);

            if (eval.getScore() > bestScore) {
                bestScore = eval.getScore();
                bestOriginal = historical;
            }
        }

        if (bestOriginal != null && newComplaint.getParentComplaint() == null) {
            newComplaint.setParentComplaint(bestOriginal);
            complaintRepository.save(newComplaint);

            if (actor != null) {
                ComplaintHistory history = new ComplaintHistory();
                history.setComplaint(newComplaint);
                history.setPreviousStatus(newComplaint.getStatus());
                history.setNewStatus(newComplaint.getStatus());
                history.setRemarks("System detected possible recurrence of Complaint #"
                        + bestOriginal.getComplaintId() + " (" + bestScore + "% match).");
                history.setUpdatedByUser(actor);
                historyRepository.save(history);
            }
        }

        return createdRecords;
    }

    public RecurrenceRecord createManualRecurrenceLink(Complaint original, Complaint newComplaint, User reporter) {
        if (original == null || newComplaint == null
                || Objects.equals(original.getComplaintId(), newComplaint.getComplaintId())) {
            return null;
        }
        if (recurrenceRepository.existsByOriginalComplaintComplaintIdAndRecurringComplaintComplaintId(
                original.getComplaintId(), newComplaint.getComplaintId())) {
            return null;
        }

        EvaluationResult eval = evaluateCandidate(original, newComplaint);
        int score = Math.max(eval.getScore(), 75);
        String reason = "Citizen explicitly reported recurrence of Complaint #" + original.getComplaintId()
                + ". Engine verification: " + eval.getReason();

        RecurrenceRecord record = new RecurrenceRecord();
        record.setOriginalComplaint(original);
        record.setRecurringComplaint(newComplaint);
        record.setMatchingScore(score);
        record.setMatchingReason(reason);
        record.setVerificationStatus(RecurrenceRecord.STATUS_PENDING);
        RecurrenceRecord saved = recurrenceRepository.save(record);

        if (reporter != null) {
            ComplaintHistory history = new ComplaintHistory();
            history.setComplaint(newComplaint);
            history.setPreviousStatus(newComplaint.getStatus());
            history.setNewStatus(newComplaint.getStatus());
            history.setRemarks("Citizen linked report as a recurrence of Complaint #"
                    + original.getComplaintId() + " (flagged for admin verification).");
            history.setUpdatedByUser(reporter);
            historyRepository.save(history);
        }
        return saved;
    }

    public EvaluationResult evaluateCandidate(Complaint historical, Complaint current) {
        if (historical == null || current == null) {
            return new EvaluationResult(false, 0, "Invalid complaints.");
        }
        Integer histCatId = historical.getCategory() != null ? historical.getCategory().getCategoryId() : null;
        Integer currCatId = current.getCategory() != null ? current.getCategory().getCategoryId() : null;
        if (histCatId == null || !Objects.equals(histCatId, currCatId)) {
            return new EvaluationResult(false, 0, "Category mismatch.");
        }
        if (!Complaint.STATUS_RESOLVED.equals(historical.getStatus())
                && !Complaint.STATUS_CLOSED.equals(historical.getStatus())) {
            return new EvaluationResult(false, 0, "Historical complaint is not yet resolved.");
        }

        int score = 0;
        List<String> reasons = new ArrayList<>();

        score += 30;
        reasons.add("Same civic category (+30)");

        double maxDistanceMeters = appProperties.getRecurrence().getDistanceMeters();
        LocationUtil.LocationMatchResult locMatch = LocationUtil.evaluateLocationMatch(
                historical.getArea(), historical.getCity(), historical.getState(), historical.getLandmark(),
                historical.getLatitude(), historical.getLongitude(),
                current.getArea(), current.getCity(), current.getState(), current.getLandmark(),
                current.getLatitude(), current.getLongitude(),
                maxDistanceMeters
        );
        score += locMatch.getScore();
        reasons.add(locMatch.getReason() + " (+" + locMatch.getScore() + ")");

        double minSimilarity = appProperties.getRecurrence().getMinSimilarity();
        String histText = (historical.getTitle() == null ? "" : historical.getTitle())
                + " " + (historical.getDescription() == null ? "" : historical.getDescription());
        String currText = (current.getTitle() == null ? "" : current.getTitle())
                + " " + (current.getDescription() == null ? "" : current.getDescription());

        double jaccard = TextSimilarityUtil.jaccardSimilarity(histText, currText);
        int descScore = TextSimilarityUtil.scoreDescriptionSimilarity(histText, currText, minSimilarity);
        score += descScore;
        if (descScore > 0) {
            reasons.add("Issue text keyword overlap " + Math.round(jaccard * 100) + "% (+" + descScore + ")");
        } else {
            reasons.add("Low text overlap " + Math.round(jaccard * 100) + "% (+0)");
        }

        score += 10;
        if (historical.getResolvedAt() != null && current.getSubmittedAt() != null) {
            long days = Math.max(0, ChronoUnit.DAYS.between(historical.getResolvedAt(), current.getSubmittedAt()));
            reasons.add("Historical complaint #" + historical.getComplaintId()
                    + " was marked " + historical.getStatus() + " " + days + " day(s) earlier (+10)");
        } else {
            reasons.add("Historical complaint #" + historical.getComplaintId()
                    + " was previously marked " + historical.getStatus() + " (+10)");
        }

        int threshold = appProperties.getRecurrence().getThreshold();
        boolean eligible = score >= threshold && locMatch.getScore() >= 30 && descScore > 0;
        String summary = String.join("; ", reasons) + ". Total confidence score: " + score + "/100.";
        return new EvaluationResult(eligible, score, summary);
    }

    public List<RecurrenceDTO> listRecurrences(String statusFilter) {
        String cleanStatus = null;
        if (!ValidationUtil.isBlank(statusFilter) && !"ALL".equalsIgnoreCase(statusFilter.trim())) {
            cleanStatus = statusFilter.trim().toUpperCase();
        }
        return recurrenceRepository.findAllDetailed(cleanStatus).stream()
                .map(RecurrenceDTO::fromEntity)
                .toList();
    }

    public RecurrenceDTO getRecurrenceById(Integer recurrenceId) {
        RecurrenceRecord record = recurrenceRepository.findDetailedById(recurrenceId)
                .orElseThrow(() -> new ServiceException(404, "Recurrence record not found."));
        return RecurrenceDTO.fromEntity(record);
    }

    public RecurrenceDTO verifyRecurrence(Integer recurrenceId, String decision, String remarks, Integer adminUserId) {
        String cleanDecision = decision == null ? "" : decision.trim().toUpperCase();
        if (!ValidationUtil.isValidRecurrenceVerification(cleanDecision)) {
            throw new ServiceException(400, "Verification status must be CONFIRMED or REJECTED.");
        }

        String cleanRemarks = ValidationUtil.clean(remarks);
        if (ValidationUtil.isBlank(cleanRemarks)) {
            cleanRemarks = RecurrenceRecord.STATUS_CONFIRMED.equals(cleanDecision)
                    ? "Verified by administrator as a genuine recurrence of a resolved issue."
                    : "Marked as unrelated / non-recurring by administrator.";
        }

        RecurrenceRecord record = recurrenceRepository.findDetailedById(recurrenceId)
                .orElseThrow(() -> new ServiceException(404, "Recurrence record not found."));
        User admin = userRepository.findById(adminUserId)
                .orElseThrow(() -> new ServiceException(404, "Administrator user not found."));

        record.setVerificationStatus(cleanDecision);
        record.setVerifiedByUser(admin);
        record.setVerifiedAt(Instant.now());
        record.setVerificationRemarks(cleanRemarks);
        RecurrenceRecord saved = recurrenceRepository.save(record);

        Complaint recurringComplaint = record.getRecurringComplaint();
        Complaint originalComplaint = record.getOriginalComplaint();

        if (RecurrenceRecord.STATUS_CONFIRMED.equals(cleanDecision)) {
            recurringComplaint.setParentComplaint(originalComplaint);
            complaintRepository.save(recurringComplaint);
        } else if (RecurrenceRecord.STATUS_REJECTED.equals(cleanDecision)) {
            boolean hasOther = recurrenceRepository.existsOtherNonRejectedForRecurring(
                    recurringComplaint.getComplaintId(), recurrenceId);
            if (!hasOther) {
                recurringComplaint.setParentComplaint(null);
                complaintRepository.save(recurringComplaint);
            }
        }

        ComplaintHistory history = new ComplaintHistory();
        history.setComplaint(recurringComplaint);
        history.setPreviousStatus(recurringComplaint.getStatus());
        history.setNewStatus(recurringComplaint.getStatus());
        history.setRemarks("Admin marked recurrence link with Complaint #"
                + originalComplaint.getComplaintId() + " as " + cleanDecision + ": " + cleanRemarks);
        history.setUpdatedByUser(admin);
        historyRepository.save(history);

        return RecurrenceDTO.fromEntity(saved);
    }
}
