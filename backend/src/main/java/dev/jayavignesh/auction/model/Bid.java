package dev.jayavignesh.auction.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class Bid {

    private long id;
    private long auctionId;
    private long bidderId;
    private BigDecimal amount;
    private LocalDateTime placedAt;
    private boolean autoBid;

    public Bid() {
    }

    public Bid(
            long id,
            long auctionId,
            long bidderId,
            BigDecimal amount,
            LocalDateTime placedAt,
            boolean autoBid) {

        this.id = id;
        this.auctionId = auctionId;
        this.bidderId = bidderId;
        this.amount = amount;
        this.placedAt = placedAt;
        this.autoBid = autoBid;
    }

    public Bid(
            long auctionId,
            long bidderId,
            BigDecimal amount,
            boolean autoBid) {

        this.auctionId = auctionId;
        this.bidderId = bidderId;
        this.amount = amount;
        this.autoBid = autoBid;
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

    public long getBidderId() {
        return bidderId;
    }

    public void setBidderId(long bidderId) {
        this.bidderId = bidderId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public LocalDateTime getPlacedAt() {
        return placedAt;
    }

    public void setPlacedAt(LocalDateTime placedAt) {
        this.placedAt = placedAt;
    }

    public boolean isAutoBid() {
        return autoBid;
    }

    public void setAutoBid(boolean autoBid) {
        this.autoBid = autoBid;
    }

    @Override
    public String toString() {
        return "Bid{" +
                "id=" + id +
                ", auctionId=" + auctionId +
                ", bidderId=" + bidderId +
                ", amount=" + amount +
                ", placedAt=" + placedAt +
                ", autoBid=" + autoBid +
                '}';
    }
}