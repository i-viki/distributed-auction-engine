package dev.jayavignesh.auction.controller;

import dev.jayavignesh.auction.dto.PlaceBidRequest;
import dev.jayavignesh.auction.exception.BadCredentialsException;
import dev.jayavignesh.auction.model.Bid;
import dev.jayavignesh.auction.service.BidService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Dedicated Controller for Bidding Operations:
 * Manages concurrent bid placement and audit trail retrieval.
 */
@RestController
@RequestMapping("/api/auctions/{auctionId}/bids")
@Tag(name = "2. Bidding Engine", description = "Endpoints for placing concurrent bids and retrieving audit history")
public class BidController {

    private final BidService bidService;

    public BidController(BidService bidService) {
        this.bidService = bidService;
    }

    @PostMapping
    @Operation(summary = "Place a concurrent bid", description = "Submits a bid against an auction with optimistic locking validation and real-time WebSocket broadcast")
    public Bid placeBid(
            @PathVariable long auctionId,
            @Valid @RequestBody PlaceBidRequest request,
            Authentication authentication) {

        long bidderId = extractUserId(authentication);

        return bidService.placeBid(
                auctionId,
                bidderId,
                request
        );
    }

    @GetMapping
    @Operation(summary = "Get bid history", description = "Fetches the complete audit trail of all bids placed on this auction")
    public List<Bid> getBids(
            @PathVariable long auctionId) {

        return bidService.getBidHistory(auctionId);
    }

    private long extractUserId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof Long userId)) {
            throw new BadCredentialsException("Authentication required.");
        }
        return userId;
    }
}

