package dev.jayavignesh.auction.service;

import dev.jayavignesh.auction.dto.LoginRequest;
import dev.jayavignesh.auction.dto.LoginResponse;
import dev.jayavignesh.auction.dto.RegisterRequest;
import dev.jayavignesh.auction.dto.RegisterResponse;
import dev.jayavignesh.auction.exception.BadCredentialsException;
import dev.jayavignesh.auction.model.User;
import dev.jayavignesh.auction.security.JwtService;
import org.springframework.stereotype.Service;

/**
 * Authentication Service:
 * Coordinates user registration, credential validation, and JWT token issuance.
 */
@Service
public class AuthService {

    private final UserService userService;
    private final JwtService jwtService;

    public AuthService(UserService userService, JwtService jwtService) {
        this.userService = userService;
        this.jwtService = jwtService;
    }

    public RegisterResponse register(RegisterRequest request) {
        User savedUser = userService.registerUser(
                request.getName(),
                request.getEmail(),
                request.getPassword()
        );

        return new RegisterResponse(
                savedUser.getId(),
                savedUser.getName(),
                savedUser.getEmail(),
                savedUser.getCreatedAt()
        );
    }

    public LoginResponse login(LoginRequest request) {
        User user = userService.findByEmail(request.getEmail());

        if (user == null ||
                !userService.verifyPassword(request.getPassword(), user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid email or password");
        }

        String token = jwtService.generateToken(user.getId(), user.getEmail());

        return new LoginResponse(
                token,
                "Bearer",
                user.getId(),
                user.getName(),
                user.getEmail()
        );
    }
}
