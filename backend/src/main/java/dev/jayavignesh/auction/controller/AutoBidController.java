package dev.jayavignesh.auction.controller;

import dev.jayavignesh.auction.dto.AutoBidRequest;
import dev.jayavignesh.auction.exception.BadCredentialsException;
import dev.jayavignesh.auction.model.AutoBid;
import dev.jayavignesh.auction.service.AutoBidService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auctions")
@Tag(name = "3. Auto-Bidding Bots", description = "Endpoints to configure and manage automated proxy bidding agents")
public class AutoBidController {

    private final AutoBidService autoBidService;

    public AutoBidController(
            AutoBidService autoBidService) {

        this.autoBidService = autoBidService;
    }

    // ---------------------------------------------------------
    // SET / UPDATE AUTO-BID
    // ---------------------------------------------------------

    @PostMapping("/{auctionId}/auto-bid")
    @Operation(summary = "Set or update auto-bid proxy", description = "Configures a maximum bid threshold for the user. The bot automatically outbids competitors up to this ceiling.")
    public ResponseEntity<AutoBid> setAutoBid(
            @PathVariable long auctionId,
            @Valid @RequestBody AutoBidRequest request,
            Authentication authentication) {

        long userId = extractUserId(authentication);

        AutoBid autoBid = autoBidService.setAutoBid(
                auctionId,
                userId,
                request
        );

        return ResponseEntity.ok(autoBid);
    }

    // ---------------------------------------------------------
    // DEACTIVATE AUTO-BID
    // ---------------------------------------------------------

    @DeleteMapping("/{auctionId}/auto-bid")
    @Operation(summary = "Deactivate auto-bid", description = "Cancels active auto-bidding proxy for the authenticated user on this auction")
    public ResponseEntity<Map<String, String>> deactivateAutoBid(
            @PathVariable long auctionId,
            Authentication authentication) {

        long userId = extractUserId(authentication);

        autoBidService.deactivateAutoBid(
                auctionId,
                userId
        );

        return ResponseEntity.ok(Map.of("message", "Auto-bid deactivated successfully."));
    }

    private long extractUserId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof Long userId)) {
            throw new BadCredentialsException("Authentication required.");
        }
        return userId;
    }
}
