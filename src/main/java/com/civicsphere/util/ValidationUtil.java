package com.civicsphere.util;

import com.civicsphere.model.Complaint;
import com.civicsphere.model.RecurrenceRecord;

import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Input validation and state-transition verification helpers.
 */
public final class ValidationUtil {

    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}$"
    );

    private static final Pattern PHONE_PATTERN = Pattern.compile(
            "^[+]?[0-9\\-\\s()]{7,20}$"
    );

    private static final Map<String, Set<String>> ALLOWED_STATUS_TRANSITIONS = Map.of(
            Complaint.STATUS_SUBMITTED, Set.of(
                    Complaint.STATUS_UNDER_REVIEW,
                    Complaint.STATUS_ASSIGNED
            ),
            Complaint.STATUS_UNDER_REVIEW, Set.of(
                    Complaint.STATUS_ASSIGNED,
                    Complaint.STATUS_IN_PROGRESS,
                    Complaint.STATUS_RESOLVED
            ),
            Complaint.STATUS_ASSIGNED, Set.of(
                    Complaint.STATUS_UNDER_REVIEW,
                    Complaint.STATUS_IN_PROGRESS,
                    Complaint.STATUS_RESOLVED
            ),
            Complaint.STATUS_IN_PROGRESS, Set.of(
                    Complaint.STATUS_ASSIGNED,
                    Complaint.STATUS_RESOLVED
            ),
            Complaint.STATUS_RESOLVED, Set.of(
                    Complaint.STATUS_CLOSED
            ),
            Complaint.STATUS_CLOSED, Set.of()
    );

    private ValidationUtil() {
    }

    public static String clean(String value) {
        return value == null ? null : value.trim();
    }

    public static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    public static boolean isValidEmail(String email) {
        return email != null && EMAIL_PATTERN.matcher(email.trim()).matches() && email.trim().length() <= 150;
    }

    public static boolean isValidPhone(String phone) {
        if (phone == null) {
            return false;
        }
        String trimmed = phone.trim();
        if (!PHONE_PATTERN.matcher(trimmed).matches()) {
            return false;
        }
        String digitsOnly = trimmed.replaceAll("[^0-9]", "");
        return digitsOnly.length() >= 7 && digitsOnly.length() <= 15;
    }

    public static boolean isValidPassword(String password) {
        return password != null && password.length() >= 8 && password.length() <= 128;
    }

    public static boolean isValidCoordinates(Double latitude, Double longitude) {
        if (latitude == null && longitude == null) {
            return true;
        }
        if (latitude == null || longitude == null) {
            return false;
        }
        return !Double.isNaN(latitude) && !Double.isNaN(longitude)
                && latitude >= -90.0 && latitude <= 90.0
                && longitude >= -180.0 && longitude <= 180.0;
    }

    public static boolean isValidComplaintStatus(String status) {
        return status != null && ALLOWED_STATUS_TRANSITIONS.containsKey(status);
    }

    public static boolean isValidStatusTransition(String currentStatus, String nextStatus) {
        if (currentStatus == null || nextStatus == null) {
            return false;
        }
        Set<String> allowed = ALLOWED_STATUS_TRANSITIONS.get(currentStatus);
        return allowed != null && allowed.contains(nextStatus);
    }

    public static boolean isValidRecurrenceVerification(String status) {
        return RecurrenceRecord.STATUS_CONFIRMED.equals(status)
                || RecurrenceRecord.STATUS_REJECTED.equals(status);
    }
}

