package dev.jayavignesh.auction.model;

import java.math.BigDecimal;

public class AutoBid {

    private long id;
    private long auctionId;
    private long userId;
    private BigDecimal maxAmount;
    private boolean active;

    public AutoBid() {
    }

    public AutoBid(
            long id,
            long auctionId,
            long userId,
            BigDecimal maxAmount,
            boolean active) {

        this.id = id;
        this.auctionId = auctionId;
        this.userId = userId;
        this.maxAmount = maxAmount;
        this.active = active;
    }

    public AutoBid(
            long auctionId,
            long userId,
            BigDecimal maxAmount,
            boolean active) {

        this.auctionId = auctionId;
        this.userId = userId;
        this.maxAmount = maxAmount;
        this.active = active;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public long getAuctionId() {
        return auctionId;
    }

    public void setAuctionId(long auctionId) {
        this.auctionId = auctionId;
    }

    public long getUserId() {
        return userId;
    }

    public void setUserId(long userId) {
        this.userId = userId;
    }

    public BigDecimal getMaxAmount() {
        return maxAmount;
    }

    public void setMaxAmount(BigDecimal maxAmount) {
        this.maxAmount = maxAmount;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    @Override
    public String toString() {
        return "AutoBid{" +
                "id=" + id +
                ", auctionId=" + auctionId +
                ", userId=" + userId +
                ", maxAmount=" + maxAmount +
                ", active=" + active +
                '}';
    }
}