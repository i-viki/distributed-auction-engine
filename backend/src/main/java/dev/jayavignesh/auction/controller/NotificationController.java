package dev.jayavignesh.auction.controller;

import dev.jayavignesh.auction.dto.NotificationResponse;
import dev.jayavignesh.auction.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/users/me")
@Tag(name = "4. User Notifications", description = "Endpoints for retrieving user alerts (e.g. outbid notifications, auction won)")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping("/notifications")
    @Operation(summary = "Get user notifications", description = "Retrieves all personal notifications (e.g. OUTBID, WON) for the authenticated user")
    public ResponseEntity<List<NotificationResponse>> getMyNotifications(
            Authentication authentication) {

        if (authentication == null || !(authentication.getPrincipal() instanceof Long userId)) {
            return ResponseEntity.status(401).build();
        }

        List<NotificationResponse> notifications =
                notificationService.getUserNotifications(userId);

        return ResponseEntity.ok(notifications);
    }
}
