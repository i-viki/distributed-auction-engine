package dev.jayavignesh.auction.dto;

import java.time.LocalDateTime;

public record NotificationResponse(
        long id,
        long userId,
        long auctionId,
        String auctionTitle,
        String type,
        LocalDateTime sentAt
) {
    public long getId() {
        return id;
    }

    public long getUserId() {
        return userId;
    }

    public long getAuctionId() {
        return auctionId;
    }

    public String getAuctionTitle() {
        return auctionTitle;
    }

    public String getType() {
        return type;
    }

    public LocalDateTime getSentAt() {
        return sentAt;
    }
}
