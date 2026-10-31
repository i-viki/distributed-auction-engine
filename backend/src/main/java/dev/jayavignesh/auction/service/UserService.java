package dev.jayavignesh.auction.service;

import dev.jayavignesh.auction.dao.UserDAO;
import dev.jayavignesh.auction.exception.BusinessValidationException;
import dev.jayavignesh.auction.model.User;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class UserService {

    private final UserDAO userDAO;
    private final PasswordEncoder passwordEncoder;

    public UserService(
            UserDAO userDAO,
            PasswordEncoder passwordEncoder) {

        this.userDAO = userDAO;
        this.passwordEncoder = passwordEncoder;
    }

    public User registerUser(
            String name,
            String email,
            String password) {

        validateRegistrationData(name, email, password);

        String normalizedEmail =
                email.trim().toLowerCase();

        User existingUser =
                userDAO.findByEmail(normalizedEmail);

        if (existingUser != null) {
            throw new BusinessValidationException(
                    "An account with this email already exists."
            );
        }

        String passwordHash =
                passwordEncoder.encode(password);

        User user = new User(
                name.trim(),
                normalizedEmail,
                passwordHash
        );

        return userDAO.save(user);
    }

    public User findByEmail(String email) {

        if (email == null || email.trim().isEmpty()) {
            return null;
        }

        return userDAO.findByEmail(
                email.trim().toLowerCase()
        );
    }

    public boolean verifyPassword(
            String rawPassword,
            String passwordHash) {

        if (rawPassword == null || passwordHash == null) {
            return false;
        }

        return passwordEncoder.matches(
                rawPassword,
                passwordHash
        );
    }

    private void validateRegistrationData(
            String name,
            String email,
            String password) {

        if (name == null || name.trim().isEmpty()) {
            throw new BusinessValidationException("Name cannot be empty.");
        }

        if (name.trim().length() > 100) {
            throw new BusinessValidationException("Name must not exceed 100 characters.");
        }

        if (email == null || email.trim().isEmpty()) {
            throw new BusinessValidationException("Email cannot be empty.");
        }

        if (email.trim().length() > 150) {
            throw new BusinessValidationException("Email must not exceed 150 characters.");
        }

        if (!email.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$")) {
            throw new BusinessValidationException("Invalid email address.");
        }

        if (password == null || password.length() < 8) {
            throw new BusinessValidationException("Password must contain at least 8 characters.");
        }
    }
}
