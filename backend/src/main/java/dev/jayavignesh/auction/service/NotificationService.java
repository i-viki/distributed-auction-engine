package dev.jayavignesh.auction.service;

import dev.jayavignesh.auction.dao.NotificationDAO;
import dev.jayavignesh.auction.dto.NotificationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Notification Service:
 * Manages notification queries and centralized dispatch for OUTBID and WON events.
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationDAO notificationDAO;

    public NotificationService(NotificationDAO notificationDAO) {
        this.notificationDAO = notificationDAO;
    }

    public List<NotificationResponse> getUserNotifications(long userId) {
        if (userId <= 0) {
            return List.of();
        }
        return notificationDAO.findByUserId(userId);
    }

    public void sendOutbidNotification(long userId, long auctionId) {
        createNotification(userId, auctionId, "OUTBID");
    }

    public void sendAuctionWonNotification(long userId, long auctionId) {
        createNotification(userId, auctionId, "WON");
    }

    public void createNotification(long userId, long auctionId, String type) {
        try {
            notificationDAO.createNotification(userId, auctionId, type);
            log.info("[Notification] Created {} alert for User #{} on Auction #{}", type, userId, auctionId);
        } catch (Exception e) {
            log.error("[Notification] Failed to create {} alert for User #{}: {}", type, userId, e.getMessage());
        }
    }
}
