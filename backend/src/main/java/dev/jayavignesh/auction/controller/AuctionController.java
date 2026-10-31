package dev.jayavignesh.auction.controller;

import dev.jayavignesh.auction.dto.CreateAuctionRequest;
import dev.jayavignesh.auction.exception.BadCredentialsException;
import dev.jayavignesh.auction.model.Auction;
import dev.jayavignesh.auction.service.AuctionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

/**
 * Dedicated Controller for Auction Lifecycle & Catalog:
 * Handles auction search, pagination, creation, and detail retrieval.
 */
@RestController
@RequestMapping("/api/auctions")
@Tag(name = "2. Auction Catalog", description = "Endpoints for managing auctions, pagination search, and lifecycle")
public class AuctionController {

    private final AuctionService auctionService;

    public AuctionController(AuctionService auctionService) {
        this.auctionService = auctionService;
    }

    // ---------------------------------------------------------
    // GET / SEARCH / FILTER AUCTIONS WITH PAGINATION
    // ---------------------------------------------------------

    @GetMapping
    @Operation(summary = "Get paginated auctions", description = "Filter auctions by status, search keyword, price range, and ending soon flag")
    public List<Auction> getAllAuctions(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(defaultValue = "false") boolean endingSoon,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        return auctionService.getAllAuctions(
                status,
                search,
                minPrice,
                maxPrice,
                endingSoon,
                page,
                size
        );
    }

    // ---------------------------------------------------------
    // GET AUCTION BY ID
    // ---------------------------------------------------------

    @GetMapping("/{id}")
    @Operation(summary = "Get auction by ID", description = "Retrieves full auction details including current highest bid and version")
    public Auction getAuctionById(
            @PathVariable long id) {

        return auctionService.getAuctionById(id);
    }

    // ---------------------------------------------------------
    // CREATE AUCTION
    // ---------------------------------------------------------

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create an auction", description = "Schedules a new auction with starting price, description, and time window")
    public Auction createAuction(
            @Valid @RequestBody CreateAuctionRequest request,
            Authentication authentication) {

        long sellerId = extractUserId(authentication);

        return auctionService.createAuction(
                sellerId,
                request
        );
    }

    private long extractUserId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof Long userId)) {
            throw new BadCredentialsException("Authentication required.");
        }
        return userId;
    }
}
