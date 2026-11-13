package com.civicsphere.service;

import com.civicsphere.model.User;
import com.civicsphere.repository.UserRepository;
import com.civicsphere.util.ValidationUtil;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public User registerCitizen(String fullName, String email, String phone,
                                String address, String password, String confirmPassword) {
        String cleanName = ValidationUtil.clean(fullName);
        String cleanEmail = ValidationUtil.clean(email);
        String cleanPhone = ValidationUtil.clean(phone);
        String cleanAddress = ValidationUtil.clean(address);

        if (ValidationUtil.isBlank(cleanName) || cleanName.length() < 2 || cleanName.length() > 120) {
            throw new ServiceException(400, "Full name must be between 2 and 120 characters.");
        }
        if (!ValidationUtil.isValidEmail(cleanEmail)) {
            throw new ServiceException(400, "Please enter a valid email address.");
        }
        if (!ValidationUtil.isValidPhone(cleanPhone)) {
            throw new ServiceException(400, "Please enter a valid phone number (7 to 15 digits).");
        }
        if (ValidationUtil.isBlank(cleanAddress) || cleanAddress.length() < 5 || cleanAddress.length() > 255) {
            throw new ServiceException(400, "Address must be between 5 and 255 characters.");
        }
        if (!ValidationUtil.isValidPassword(password)) {
            throw new ServiceException(400, "Password must be between 8 and 128 characters.");
        }
        if (confirmPassword != null && !password.equals(confirmPassword)) {
            throw new ServiceException(400, "Password and confirm password do not match.");
        }

        if (userRepository.existsByEmailIgnoreCase(cleanEmail)) {
            throw new ServiceException(409, "An account with this email already exists.");
        }

        User user = new User();
        user.setFullName(cleanName);
        user.setEmail(cleanEmail.toLowerCase());
        user.setPhone(cleanPhone);
        user.setAddress(cleanAddress);
        user.setPasswordHash(passwordEncoder.encode(password));
        // Always enforce CITIZEN role on public registration; never trust client role
        user.setRole(User.ROLE_CITIZEN);

        return userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public User authenticate(String email, String password) {
        String cleanEmail = ValidationUtil.clean(email);
        if (ValidationUtil.isBlank(cleanEmail) || password == null || password.isEmpty()) {
            throw new ServiceException(400, "Email and password are required.");
        }

        User user = userRepository.findByEmailIgnoreCase(cleanEmail).orElse(null);
        if (user == null || !passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new ServiceException(401, "Invalid email or password.");
        }
        return user;
    }

    @Transactional(readOnly = true)
    public User getById(Integer userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ServiceException(404, "User account not found."));
    }
}
