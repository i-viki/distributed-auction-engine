package dev.jayavignesh.auction.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CreateAuctionRequest(
        @NotBlank(message = "Title cannot be empty.")
        String title,

        @NotBlank(message = "Description cannot be empty.")
        String description,

        String imageUrl,

        @NotNull(message = "Starting price is required.")
        @DecimalMin(
                value = "0.01",
                message = "Starting price must be greater than zero."
        )
        BigDecimal startingPrice,

        @Min(value = 1, message = "Duration must be at least 1 minute.")
        Integer durationMinutes,

        LocalDateTime startTime,

        LocalDateTime endTime
) {
    public CreateAuctionRequest(
            String title,
            String description,
            String imageUrl,
            BigDecimal startingPrice,
            LocalDateTime startTime,
            LocalDateTime endTime) {
        this(title, description, imageUrl, startingPrice, null, startTime, endTime);
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public BigDecimal getStartingPrice() {
        return startingPrice;
    }

    public Integer getDurationMinutes() {
        return durationMinutes;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public LocalDateTime getEndTime() {
        return endTime;
    }
}
