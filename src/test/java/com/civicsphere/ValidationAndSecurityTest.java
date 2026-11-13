package com.civicsphere;

import com.civicsphere.model.Complaint;
import com.civicsphere.util.FileUploadUtil;
import com.civicsphere.util.ValidationUtil;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;

class ValidationAndSecurityTest {

    @Test
    void passwordHashingAndVerificationWithSpringSecurityBCrypt() {
        PasswordEncoder encoder = new BCryptPasswordEncoder(12);
        String raw = "SecureCivic#2026";
        String hash = encoder.encode(raw);

        assertNotEquals(raw, hash);
        assertTrue(hash.startsWith("$2a$") || hash.startsWith("$2b$"));
        assertTrue(encoder.matches(raw, hash));
        assertFalse(encoder.matches("WrongPass123", hash));
    }

    @Test
    void statusTransitionsAreStrictlyEnforced() {
        assertTrue(ValidationUtil.isValidStatusTransition(Complaint.STATUS_SUBMITTED, Complaint.STATUS_UNDER_REVIEW));
        assertTrue(ValidationUtil.isValidStatusTransition(Complaint.STATUS_SUBMITTED, Complaint.STATUS_ASSIGNED));
        assertTrue(ValidationUtil.isValidStatusTransition(Complaint.STATUS_ASSIGNED, Complaint.STATUS_IN_PROGRESS));
        assertTrue(ValidationUtil.isValidStatusTransition(Complaint.STATUS_IN_PROGRESS, Complaint.STATUS_RESOLVED));
        assertTrue(ValidationUtil.isValidStatusTransition(Complaint.STATUS_RESOLVED, Complaint.STATUS_CLOSED));

        assertFalse(ValidationUtil.isValidStatusTransition(Complaint.STATUS_SUBMITTED, Complaint.STATUS_CLOSED));
        assertFalse(ValidationUtil.isValidStatusTransition(Complaint.STATUS_CLOSED, Complaint.STATUS_SUBMITTED));
        assertFalse(ValidationUtil.isValidStatusTransition(Complaint.STATUS_RESOLVED, Complaint.STATUS_SUBMITTED));
        assertFalse(ValidationUtil.isValidStatusTransition(Complaint.STATUS_IN_PROGRESS, Complaint.STATUS_SUBMITTED));
    }

    @Test
    void inputValidatorsWorkCorrectly() {
        assertTrue(ValidationUtil.isValidEmail("citizen@example.com"));
        assertFalse(ValidationUtil.isValidEmail("not-an-email"));

        assertTrue(ValidationUtil.isValidPhone("+91 9876543210"));
        assertFalse(ValidationUtil.isValidPhone("123"));

        assertTrue(ValidationUtil.isValidCoordinates(17.4948, 78.3996));
        assertFalse(ValidationUtil.isValidCoordinates(17.4948, null));
        assertFalse(ValidationUtil.isValidCoordinates(95.0, 78.3996));
    }

    @Test
    void uploadPathTraversalIsRejected() {
        assertNull(FileUploadUtil.resolveSafeUploadPath("../WEB-INF/web.xml"));
        assertNull(FileUploadUtil.resolveSafeUploadPath("complaints/../../secret.txt"));
        assertNull(FileUploadUtil.resolveSafeUploadPath("other/0123456789abcdef0123456789abcdef.jpg"));
    }
}
