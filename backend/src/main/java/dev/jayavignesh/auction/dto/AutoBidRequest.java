package dev.jayavignesh.auction.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record AutoBidRequest(
        @NotNull(message = "Maximum auto-bid amount is required.")
        @DecimalMin(
                value = "0.01",
                message = "Maximum auto-bid amount must be greater than zero."
        )
        BigDecimal maxAmount
) {
    public BigDecimal getMaxAmount() {
        return maxAmount;
    }
}
