package dev.jayavignesh.auction.dto;

public record LoginResponse(
        String token,
        String tokenType,
        long userId,
        String name,
        String email
) {
    public String getToken() {
        return token;
    }

    public String getTokenType() {
        return tokenType;
    }

    public long getUserId() {
        return userId;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }
}
