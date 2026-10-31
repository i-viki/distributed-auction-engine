package dev.jayavignesh.auction.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class BidUpdateMessage {

    private long auctionId;
    private long bidId;
    private long bidderId;
    private BigDecimal amount;
    private LocalDateTime placedAt;
    private boolean autoBid;

    private BigDecimal currentHighestBid;
    private Long currentHighestBidderId;

    private LocalDateTime endTime;

    public BidUpdateMessage() {
    }

    public BidUpdateMessage(
            long auctionId,
            long bidId,
            long bidderId,
            BigDecimal amount,
            LocalDateTime placedAt,
            boolean autoBid,
            BigDecimal currentHighestBid,
            Long currentHighestBidderId,
            LocalDateTime endTime) {

        this.auctionId = auctionId;
        this.bidId = bidId;
        this.bidderId = bidderId;
        this.amount = amount;
        this.placedAt = placedAt;
        this.autoBid = autoBid;
        this.currentHighestBid = currentHighestBid;
        this.currentHighestBidderId = currentHighestBidderId;
        this.endTime = endTime;
    }

    public long getAuctionId() {
        return auctionId;
    }

    public void setAuctionId(long auctionId) {
        this.auctionId = auctionId;
    }

    public long getBidId() {
        return bidId;
    }

    public void setBidId(long bidId) {
        this.bidId = bidId;
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

    public BigDecimal getCurrentHighestBid() {
        return currentHighestBid;
    }

    public void setCurrentHighestBid(
            BigDecimal currentHighestBid) {
        this.currentHighestBid = currentHighestBid;
    }

    public Long getCurrentHighestBidderId() {
        return currentHighestBidderId;
    }

    public void setCurrentHighestBidderId(
            Long currentHighestBidderId) {
        this.currentHighestBidderId =
                currentHighestBidderId;
    }

    public LocalDateTime getEndTime() {
        return endTime;
    }

    public void setEndTime(
            LocalDateTime endTime) {
        this.endTime = endTime;
    }
}