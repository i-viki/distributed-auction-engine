package dev.jayavignesh.auction.controller;

import dev.jayavignesh.auction.dto.LoginRequest;
import dev.jayavignesh.auction.dto.LoginResponse;
import dev.jayavignesh.auction.dto.RegisterRequest;
import dev.jayavignesh.auction.dto.RegisterResponse;
import dev.jayavignesh.auction.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/**
 * Authentication REST Controller:
 * Thin HTTP adapter for user registration and JWT authentication endpoints.
 */
@RestController
@RequestMapping("/api/auth")
@Tag(name = "1. Authentication", description = "Public endpoints for user registration and JWT authentication")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Register a new user", description = "Creates a new user account with BCrypt password encryption")
    public RegisterResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    @PostMapping("/login")
    @Operation(summary = "Login user", description = "Authenticates user credentials and returns a signed JWT Bearer token")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }
}
