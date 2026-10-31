package dev.jayavignesh.auction.dto;

import java.time.LocalDateTime;

public record RegisterResponse(
        long id,
        String name,
        String email,
        LocalDateTime createdAt
) {
    public long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
