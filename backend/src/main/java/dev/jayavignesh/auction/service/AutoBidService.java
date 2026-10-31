
package dev.jayavignesh.auction.service;

import dev.jayavignesh.auction.dao.AuctionDAO;
import dev.jayavignesh.auction.dao.AutoBidDAO;
import dev.jayavignesh.auction.dao.BidDAO;
import dev.jayavignesh.auction.dto.AutoBidRequest;
import dev.jayavignesh.auction.dto.BidUpdateMessage;
import dev.jayavignesh.auction.exception.BusinessValidationException;
import dev.jayavignesh.auction.exception.OptimisticLockException;
import dev.jayavignesh.auction.model.Auction;
import dev.jayavignesh.auction.model.AutoBid;
import dev.jayavignesh.auction.model.Bid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

@Service
public class AutoBidService {

        @Value("${auction.bidding.auto-bid-increment:10.00}")
        private BigDecimal bidIncrement = new BigDecimal("10.00");

        @Value("${auction.bidding.max-retries:3}")
        private int maxRetries = 3;

        private final AutoBidDAO autoBidDAO;
        private final AuctionDAO auctionDAO;
        private final BidDAO bidDAO;
        private final SimpMessagingTemplate messagingTemplate;
        private final RedisBiddingSequencer redisBiddingSequencer;

        public AutoBidService(
                        AutoBidDAO autoBidDAO,
                        AuctionDAO auctionDAO,
                        BidDAO bidDAO,
                        SimpMessagingTemplate messagingTemplate,
                        RedisBiddingSequencer redisBiddingSequencer) {

                this.autoBidDAO = autoBidDAO;
                this.auctionDAO = auctionDAO;
                this.bidDAO = bidDAO;
                this.messagingTemplate = messagingTemplate;
                this.redisBiddingSequencer = redisBiddingSequencer;
        }

        // ---------------------------------------------------------
        // SET / UPDATE AUTO-BID
        // ---------------------------------------------------------

        public AutoBid setAutoBid(
                        long auctionId,
                        long userId,
                        AutoBidRequest request) {

                if (auctionId <= 0) {
                        throw new BusinessValidationException(
                                        "Auction ID must be greater than zero.");
                }

                if (userId <= 0) {
                        throw new BusinessValidationException(
                                        "User ID must be greater than zero.");
                }

                if (!bidDAO.userExists(userId)) {
                        throw new IllegalArgumentException(
                                        "User not found with ID: " + userId);
                }

                if (request == null ||
                                request.getMaxAmount() == null) {

                        throw new BusinessValidationException(
                                        "Maximum auto-bid amount is required.");
                }

                if (request.getMaxAmount()
                                .compareTo(BigDecimal.ZERO) <= 0) {

                        throw new BusinessValidationException(
                                        "Maximum auto-bid amount must be greater than zero.");
                }

                Auction auction = auctionDAO.findById(auctionId);

                if (auction == null) {
                        throw new IllegalArgumentException(
                                        "Auction not found with ID: " + auctionId);
                }

                validateAuction(auction);

                if (auction.getSellerId() == userId) {
                        throw new BusinessValidationException(
                                        "Sellers are not permitted to configure auto-bids on their own auctions.");
                }

                if (request.getMaxAmount()
                                .compareTo(auction.getCurrentHighestBid()) <= 0) {

                        throw new BusinessValidationException(
                                        "Maximum auto-bid amount must be greater than "
                                                        + "the current highest bid of "
                                                        + auction.getCurrentHighestBid()
                                                        + ".");
                }

                AutoBid autoBid = new AutoBid(
                                auctionId,
                                userId,
                                request.getMaxAmount(),
                                true);

                AutoBid savedAutoBid = autoBidDAO.save(autoBid);

                // Process the new proxy setting immediately.
                processAutoBids(auctionId);

                return savedAutoBid;
        }

        // ---------------------------------------------------------
        // PROCESS AUTO-BIDS
        // ---------------------------------------------------------

        public void processAutoBids(long auctionId) {

                for (int cycle = 0; cycle < maxRetries; cycle++) {

                        Auction auction = auctionDAO.findById(auctionId);

                        if (auction == null) {
                                return;
                        }

                        if (!"LIVE".equals(auction.getStatus())) {
                                return;
                        }

                        if (auction.getEndTime() == null ||
                                        !auction.getEndTime()
                                                        .isAfter(LocalDateTime.now())) {
                                return;
                        }

                        List<AutoBid> activeAutoBids = autoBidDAO.findActiveByAuctionId(auctionId);

                        if (activeAutoBids.isEmpty()) {
                                return;
                        }

                        /*
                         * Selection rules:
                         * 1. Highest maximum amount wins.
                         * 2. Equal maximums: lowest setting ID wins.
                         *
                         * A lower ID represents an earlier-created setting.
                         */
                        AutoBid strongestAutoBid = activeAutoBids.stream()
                                        .min(
                                                        Comparator
                                                                        .comparing(
                                                                                        AutoBid::getMaxAmount,
                                                                                        Comparator.reverseOrder())
                                                                        .thenComparing(
                                                                                        AutoBid::getId))
                                        .orElse(null);

                        if (strongestAutoBid == null) {
                                return;
                        }

                        Long currentHighestBidderId = auction.getCurrentHighestBidderId();

                        /*
                         * The strongest proxy is already the highest bidder.
                         */
                        if (currentHighestBidderId != null &&
                                        currentHighestBidderId.longValue() == strongestAutoBid.getUserId()) {
                                return;
                        }

                        /*
                         * The strongest proxy cannot beat the current bid.
                         */
                        if (strongestAutoBid.getMaxAmount()
                                        .compareTo(auction.getCurrentHighestBid()) <= 0) {
                                return;
                        }

                        /*
                         * Calculate the next automatic bid.
                         */
                        BigDecimal automaticBidAmount = auction.getCurrentHighestBid()
                                        .add(bidIncrement);

                        /*
                         * Never exceed the user's configured maximum.
                         */
                        if (automaticBidAmount.compareTo(
                                        strongestAutoBid.getMaxAmount()) > 0) {

                                automaticBidAmount = strongestAutoBid.getMaxAmount();
                        }

                        if (automaticBidAmount.compareTo(
                                        auction.getCurrentHighestBid()) <= 0) {
                                return;
                        }

                        Bid automaticBid = new Bid(
                                        auctionId,
                                        strongestAutoBid.getUserId(),
                                        automaticBidAmount,
                                        true);

                        long expectedVersion = auction.getVersion();

                        Long previousHighestBidderId = auction.getCurrentHighestBidderId();

                        try {

                                Bid savedBid = bidDAO.placeBid(
                                                automaticBid,
                                                expectedVersion,
                                                previousHighestBidderId);

                                Auction latestAuction = auctionDAO.findById(auctionId);

                                if (latestAuction == null) {
                                        return;
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
                                                latestAuction.getEndTime());

                                try {
                                        redisBiddingSequencer.broadcastBid(updateMessage);
                                } catch (Exception e) {
                                        messagingTemplate.convertAndSend(
                                                        "/topic/auctions/" + auctionId,
                                                        updateMessage);
                                }

                                /*
                                 * Re-evaluate the proxy settings after each
                                 * accepted automatic bid.
                                 */
                                continue;

                        } catch (OptimisticLockException exception) {

                                /*
                                 * Another bid changed the auction.
                                 * Reload and recalculate on the next cycle.
                                 */
                                continue;
                        }
                }
        }

        // ---------------------------------------------------------
        // DEACTIVATE AUTO-BID
        // ---------------------------------------------------------

        public void deactivateAutoBid(
                        long auctionId,
                        long userId) {

                if (auctionId <= 0 || userId <= 0) {
                        throw new BusinessValidationException(
                                        "Auction ID and user ID must be greater than zero.");
                }

                autoBidDAO.deactivate(auctionId, userId);
        }

        // ---------------------------------------------------------
        // VALIDATE AUCTION
        // ---------------------------------------------------------

        private void validateAuction(Auction auction) {

                if (!"LIVE".equals(auction.getStatus())) {
                        throw new BusinessValidationException(
                                        "Auction is not LIVE.");
                }

                if (auction.getEndTime() == null ||
                                !auction.getEndTime()
                                                .isAfter(LocalDateTime.now())) {

                        throw new BusinessValidationException(
                                        "Auction has ended.");
                }
        }
}