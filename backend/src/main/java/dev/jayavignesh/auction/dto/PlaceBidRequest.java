package dev.jayavignesh.auction.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public class PlaceBidRequest {

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Bid amount must be greater than 0")
    private BigDecimal amount;

    @JsonProperty("autoBid")
    @JsonSetter(nulls = Nulls.AS_EMPTY)
    private Boolean autoBid = false;

    public PlaceBidRequest() {
    }

    public PlaceBidRequest(BigDecimal amount, Boolean autoBid) {
        this.amount = amount;
        this.autoBid = autoBid != null ? autoBid : false;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public boolean isAutoBid() {
        return Boolean.TRUE.equals(autoBid);
    }

    public void setAutoBid(Boolean autoBid) {
        this.autoBid = autoBid != null ? autoBid : false;
    }
}