package com.civicsphere;

import com.civicsphere.model.Category;
import com.civicsphere.model.Complaint;
import com.civicsphere.service.RecurrenceService;
import com.civicsphere.util.LocationUtil;
import com.civicsphere.util.TextSimilarityUtil;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.*;

class RecurrenceEngineTest {

    private final RecurrenceService recurrenceService =
            new RecurrenceService(null, null, null, null, null);

    private static Category category(int id) {
        Category c = new Category();
        c.setCategoryId(id);
        c.setName("Road Damage & Potholes");
        return c;
    }

    @Test
    void detectsKukatpallyPotholeRecurrenceAfterResolution() {
        Instant now = Instant.now();
        Category road = category(1);

        Complaint c101 = new Complaint();
        c101.setComplaintId(101);
        c101.setCategory(road);
        c101.setTitle("Deep pothole in Kukatpally near JNTU Metro");
        c101.setDescription("Large water-filled pothole on the main road near JNTU Metro causing two-wheeler skids and traffic congestion.");
        c101.setArea("Kukatpally");
        c101.setCity("Hyderabad");
        c101.setState("Telangana");
        c101.setLandmark("Near JNTU Metro Station");
        c101.setStatus(Complaint.STATUS_RESOLVED);
        c101.setSubmittedAt(now.minus(15, ChronoUnit.DAYS));
        c101.setResolvedAt(now.minus(10, ChronoUnit.DAYS));

        Complaint c145 = new Complaint();
        c145.setComplaintId(145);
        c145.setCategory(road);
        c145.setTitle("Pothole reappeared at Kukatpally JNTU Metro road");
        c145.setDescription("Deep pothole reopened on main road near JNTU Metro causing traffic congestion and two-wheeler skids.");
        c145.setArea("  kukatpally ");
        c145.setCity("HYDERABAD");
        c145.setState("telangana");
        c145.setLandmark("JNTU Metro Station");
        c145.setStatus(Complaint.STATUS_SUBMITTED);
        c145.setSubmittedAt(now);

        RecurrenceService.EvaluationResult result = recurrenceService.evaluateCandidate(c101, c145);

        assertTrue(result.isEligible(), "Expected Kukatpally pothole #145 to be flagged as recurrence candidate: " + result.getReason());
        assertTrue(result.getScore() >= 70, "Expected score >= 70, got " + result.getScore());
    }

    @Test
    void doesNotFlagCategoryOnlyMatchInDifferentCity() {
        Instant now = Instant.now();
        Category road = category(1);

        Complaint old = new Complaint();
        old.setComplaintId(10);
        old.setCategory(road);
        old.setTitle("Damaged asphalt road");
        old.setDescription("Road edge eroded due to heavy lorry movement.");
        old.setArea("Madhapur");
        old.setCity("Hyderabad");
        old.setState("Telangana");
        old.setStatus(Complaint.STATUS_RESOLVED);
        old.setSubmittedAt(now.minus(10, ChronoUnit.DAYS));
        old.setResolvedAt(now.minus(5, ChronoUnit.DAYS));

        Complaint current = new Complaint();
        current.setComplaintId(20);
        current.setCategory(road);
        current.setTitle("Broken footpath slab");
        current.setDescription("Pedestrian tiles cracked near school gate.");
        current.setArea("Indiranagar");
        current.setCity("Bengaluru");
        current.setState("Karnataka");
        current.setStatus(Complaint.STATUS_SUBMITTED);
        current.setSubmittedAt(now);

        RecurrenceService.EvaluationResult result = recurrenceService.evaluateCandidate(old, current);
        assertFalse(result.isEligible());
        assertTrue(result.getScore() < 70);
    }

    @Test
    void doesNotFlagUnresolvedHistoricalComplaint() {
        Instant now = Instant.now();
        Category road = category(1);

        Complaint unresolved = new Complaint();
        unresolved.setComplaintId(50);
        unresolved.setCategory(road);
        unresolved.setTitle("Pothole in Kukatpally");
        unresolved.setDescription("Deep pothole near JNTU Metro");
        unresolved.setArea("Kukatpally");
        unresolved.setCity("Hyderabad");
        unresolved.setState("Telangana");
        unresolved.setStatus(Complaint.STATUS_IN_PROGRESS);
        unresolved.setSubmittedAt(now.minus(5, ChronoUnit.DAYS));

        Complaint current = new Complaint();
        current.setComplaintId(51);
        current.setCategory(road);
        current.setTitle("Pothole in Kukatpally");
        current.setDescription("Deep pothole near JNTU Metro");
        current.setArea("Kukatpally");
        current.setCity("Hyderabad");
        current.setState("Telangana");
        current.setStatus(Complaint.STATUS_SUBMITTED);
        current.setSubmittedAt(now);

        RecurrenceService.EvaluationResult result = recurrenceService.evaluateCandidate(unresolved, current);
        assertFalse(result.isEligible());
    }

    @Test
    void haversineDistanceWithinAndBeyond100Meters() {
        double close = LocationUtil.haversineMeters(17.494800, 78.399600, 17.495100, 78.399700);
        assertTrue(close < 100.0, "Expected < 100m, got " + close);

        double far = LocationUtil.haversineMeters(17.494800, 78.399600, 17.505000, 78.410000);
        assertTrue(far > 100.0, "Expected > 100m, got " + far);
    }

    @Test
    void weakSingleWordOverlapReceivesZeroDescriptionPoints() {
        int score = TextSimilarityUtil.scoreDescriptionSimilarity(
                "Broken main pipeline flooding street corner near temple",
                "Broken wooden bench inside community park garden",
                0.15
        );
        assertEquals(0, score, "Weak single-word overlap should not receive description similarity points");
    }
}
