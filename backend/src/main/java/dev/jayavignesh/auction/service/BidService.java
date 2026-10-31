package dev.jayavignesh.auction.service;

import dev.jayavignesh.auction.dao.AuctionDAO;
import dev.jayavignesh.auction.dao.BidDAO;
import dev.jayavignesh.auction.dto.BidUpdateMessage;
import dev.jayavignesh.auction.dto.PlaceBidRequest;
import dev.jayavignesh.auction.exception.BusinessValidationException;
import dev.jayavignesh.auction.exception.OptimisticLockException;
import dev.jayavignesh.auction.model.Auction;
import dev.jayavignesh.auction.model.Bid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class BidService {

    private static final Logger log = LoggerFactory.getLogger(BidService.class);
    private static final int MAX_RETRIES = 3;

    private final BidDAO bidDAO;
    private final AuctionDAO auctionDAO;
    private final NotificationService notificationService;
    private final SimpMessagingTemplate messagingTemplate;
    private final AutoBidService autoBidService;
    private final RedisBiddingSequencer redisBiddingSequencer;

    public BidService(
            BidDAO bidDAO,
            AuctionDAO auctionDAO,
            NotificationService notificationService,
            SimpMessagingTemplate messagingTemplate,
            AutoBidService autoBidService,
            RedisBiddingSequencer redisBiddingSequencer) {

        this.bidDAO = bidDAO;
        this.auctionDAO = auctionDAO;
        this.notificationService = notificationService;
        this.messagingTemplate = messagingTemplate;
        this.autoBidService = autoBidService;
        this.redisBiddingSequencer = redisBiddingSequencer;
    }

    // ---------------------------------------------------------
    // PLACE BID
    // ---------------------------------------------------------

    public Bid placeBid(
            long auctionId,
            long bidderId,
            PlaceBidRequest request) {

        if (auctionId <= 0) {
            throw new BusinessValidationException(
                    "Auction ID must be greater than zero."
            );
        }

        if (bidderId <= 0) {
            throw new BusinessValidationException(
                    "Bidder ID must be greater than zero."
            );
        }

        if (!bidDAO.userExists(bidderId)) {
            throw new IllegalArgumentException(
                    "Bidder not found with ID: " + bidderId
            );
        }

        if (request == null) {
            throw new BusinessValidationException(
                    "Bid request is required."
            );
        }

        if (request.getAmount() == null
                || request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessValidationException(
                    "Bid amount must be greater than zero."
            );
        }

        Auction auction = auctionDAO.findById(auctionId);

        if (auction == null) {
            throw new IllegalArgumentException(
                    "Auction not found with ID: " + auctionId
            );
        }

        validateAuction(auction);

        if (auction.getSellerId() == bidderId) {
            throw new BusinessValidationException(
                    "Sellers are not permitted to bid on their own auctions."
            );
        }

        if (request.getAmount()
                .compareTo(auction.getCurrentHighestBid()) <= 0) {
            throw new BusinessValidationException(
                    "Your bid must be greater than the current highest bid of "
                            + auction.getCurrentHighestBid() + "."
            );
        }

        // -----------------------------------------------------
        // STEP 1: In-Memory Redis Lua Atomic Sequencer
        // -----------------------------------------------------
        try {
            redisBiddingSequencer.sequenceBid(auctionId, bidderId, request.getAmount());
        } catch (BusinessValidationException bve) {
            throw bve;
        } catch (Exception e) {
            log.warn("[Redis Sequencer] Evaluation bypassed: {}", e.getMessage());
        }

        LocalDateTime operationStartedAt =
                LocalDateTime.now().withNano(0);

        Bid bid = new Bid(
                auctionId,
                bidderId,
                request.getAmount(),
                request.isAutoBid()
        );

        long expectedVersion = auction.getVersion();

        Long previousHighestBidderId =
                auction.getCurrentHighestBidderId();

        // -----------------------------------------------------
        // STEP 2: Database Persistence & Version Check
        // -----------------------------------------------------

        for (int attempt = 1;
             attempt <= MAX_RETRIES;
             attempt++) {

            try {
                Bid savedBid = bidDAO.placeBid(
                        bid,
                        expectedVersion,
                        previousHighestBidderId
                );

                Auction latestAuction = auctionDAO.findById(auctionId);

                if (latestAuction == null) {
                    throw new IllegalArgumentException(
                            "Auction not found with ID: " + auctionId
                    );
                }

                BidUpdateMessage updateMessage = new BidUpdateMessage(
                        savedBid.getAuctionId(),
                        savedBid.getId(),
                        savedBid.getBidderId(),
                        savedBid.getAmount(),
                        savedBid.getPlacedAt(),
                        savedBid.isAutoBid(),
                        latestAuction.getCurrentHighestBid(),
                        latestAuction.getCurrentHighestBidderId(),
                        latestAuction.getEndTime()
                );

                // -----------------------------------------------------
                // STEP 3: Multi-Node Distribution (Redis Pub/Sub)
                // -----------------------------------------------------
                try {
                    redisBiddingSequencer.broadcastBid(updateMessage);
                } catch (Exception e) {
                    log.error("[Redis Pub/Sub] Failed to broadcast, falling back to local STOMP: {}", e.getMessage());
                    messagingTemplate.convertAndSend(
                            "/topic/auctions/" + auctionId,
                            updateMessage
                    );
                }

                // Allow active auto-bids to respond to the new bid.
                autoBidService.processAutoBids(auctionId);

                return savedBid;

            } catch (OptimisticLockException exception) {

                if (attempt == MAX_RETRIES) {
                    break;
                }

                Auction latestAuction = auctionDAO.findById(auctionId);

                if (latestAuction == null) {
                    throw new IllegalArgumentException(
                            "Auction not found with ID: " + auctionId
                    );
                }

                // If someone else already bid higher, stop retrying
                if (request.getAmount()
                        .compareTo(latestAuction.getCurrentHighestBid()) <= 0) {

                    if (operationStartedAt != null) {
                        notifyConcurrentLoserIfNeeded(
                                bidderId,
                                auctionId,
                                operationStartedAt
                        );
                    }

                    throw new BusinessValidationException(
                            "Your bid of " + request.getAmount()
                                    + " was outbid. Current highest bid is "
                                    + latestAuction.getCurrentHighestBid() + "."
                    );
                }

                // Our bid remains valid. Retry against the latest version.
                expectedVersion = latestAuction.getVersion();
                previousHighestBidderId =
                        latestAuction.getCurrentHighestBidderId();
            }
        }

        // All retries failed
        Auction latestAuction = auctionDAO.findById(auctionId);

        if (latestAuction == null) {
            throw new IllegalArgumentException(
                    "Auction not found with ID: " + auctionId
            );
        }

        throw new BusinessValidationException(
                "Bid could not be completed because the auction changed "
                        + "concurrently. Please retry your bid."
        );
    }

    // ---------------------------------------------------------
    // GET BID HISTORY
    // ---------------------------------------------------------

    public List<Bid> getBidHistory(long auctionId) {

        if (auctionId <= 0) {
            throw new BusinessValidationException(
                    "Auction ID must be greater than zero."
            );
        }

        if (auctionDAO.findById(auctionId) == null) {
            throw new IllegalArgumentException(
                    "Auction not found with ID: " + auctionId
            );
        }

        return bidDAO.findByAuctionId(auctionId);
    }

    // ---------------------------------------------------------
    // VALIDATE AUCTION
    // ---------------------------------------------------------

    private void validateAuction(Auction auction) {

        if (!"LIVE".equals(auction.getStatus())) {
            throw new BusinessValidationException(
                    "Auction is not LIVE."
            );
        }

        if (auction.getEndTime() == null
                || !auction.getEndTime().isAfter(LocalDateTime.now())) {
            throw new BusinessValidationException(
                    "Auction has ended."
            );
        }
    }

    // ---------------------------------------------------------
    // CONCURRENT LOSER NOTIFICATION
    // ---------------------------------------------------------

    private void notifyConcurrentLoserIfNeeded(
            long bidderId,
            long auctionId,
            LocalDateTime operationStartedAt) {

        notificationService.sendOutbidNotification(
                bidderId,
                auctionId
        );
    }
}
