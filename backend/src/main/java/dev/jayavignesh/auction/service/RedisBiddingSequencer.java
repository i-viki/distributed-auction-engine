package dev.jayavignesh.auction.service;

import dev.jayavignesh.auction.config.RedisConfig;
import dev.jayavignesh.auction.dao.AuctionDAO;
import dev.jayavignesh.auction.dto.BidUpdateMessage;
import dev.jayavignesh.auction.exception.BusinessValidationException;
import dev.jayavignesh.auction.model.Auction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Service
public class RedisBiddingSequencer {

    private static final Logger log = LoggerFactory.getLogger(RedisBiddingSequencer.class);

    @Value("${auction.anti-snipe.window-minutes:2}")
    private long antiSnipeWindowMinutes = 2;

    @Value("${auction.anti-snipe.extension-minutes:2}")
    private long antiSnipeExtensionMinutes = 2;

    private final StringRedisTemplate stringRedisTemplate;
    private final RedisTemplate<String, Object> redisTemplate;
    private final DefaultRedisScript<List> atomicBiddingScript;
    private final AuctionDAO auctionDAO;

    public RedisBiddingSequencer(
            StringRedisTemplate stringRedisTemplate,
            RedisTemplate<String, Object> redisTemplate,
            DefaultRedisScript<List> atomicBiddingScript,
            AuctionDAO auctionDAO) {

        this.stringRedisTemplate = stringRedisTemplate;
        this.redisTemplate = redisTemplate;
        this.atomicBiddingScript = atomicBiddingScript;
        this.auctionDAO = auctionDAO;
    }

    public static class SequencedBidResult {
        private final boolean success;
        private final int newVersion;
        private final LocalDateTime newEndTime;
        private final BigDecimal currentHighestBid;
        private final long currentHighestBidderId;

        public SequencedBidResult(
                boolean success,
                int newVersion,
                LocalDateTime newEndTime,
                BigDecimal currentHighestBid,
                long currentHighestBidderId) {

            this.success = success;
            this.newVersion = newVersion;
            this.newEndTime = newEndTime;
            this.currentHighestBid = currentHighestBid;
            this.currentHighestBidderId = currentHighestBidderId;
        }

        public boolean isSuccess() { return success; }
        public int getNewVersion() { return newVersion; }
        public LocalDateTime getNewEndTime() { return newEndTime; }
        public BigDecimal getCurrentHighestBid() { return currentHighestBid; }
        public long getCurrentHighestBidderId() { return currentHighestBidderId; }
    }

    /**
     * Atomically sequence a bid in Redis using Lua script evaluation.
     */
    public SequencedBidResult sequenceBid(long auctionId, long bidderId, BigDecimal amount) {
        String key = "auction:" + auctionId;

        // Ensure auction is cached in Redis
        ensureAuctionCached(auctionId, key);

        long nowMs = System.currentTimeMillis();
        long antiSnipeWindowMs = antiSnipeWindowMinutes * 60 * 1000L;
        long antiSnipeExtensionMs = antiSnipeExtensionMinutes * 60 * 1000L;

        List<?> result = stringRedisTemplate.execute(
                atomicBiddingScript,
                Collections.singletonList(key),
                amount.toPlainString(),
                String.valueOf(bidderId),
                String.valueOf(nowMs),
                String.valueOf(antiSnipeWindowMs),
                String.valueOf(antiSnipeExtensionMs)
        );

        if (result == null || result.isEmpty()) {
            throw new BusinessValidationException("Sequencer returned empty response.");
        }

        Number status = (Number) result.get(0);
        if (status.intValue() == 1) {
            // Success: [1, newVersion, newEndTimeMs, currentHighestBid, currentHighestBidderId]
            int newVersion = Integer.parseInt(result.get(1).toString());
            long newEndTimeMs = Long.parseLong(result.get(2).toString());
            BigDecimal newHighestBid = new BigDecimal(result.get(3).toString());
            long newBidderId = Long.parseLong(result.get(4).toString());

            LocalDateTime newEndTime = LocalDateTime.ofInstant(
                    Instant.ofEpochMilli(newEndTimeMs),
                    ZoneId.systemDefault()
            );

            log.info("[Redis Sequencer] Accepted Bid: Auction #{} | Version {} | Amount ${} | Bidder #{}",
                    auctionId, newVersion, newHighestBid, newBidderId);

            return new SequencedBidResult(true, newVersion, newEndTime, newHighestBid, newBidderId);
        }

        // Validation Failure Handling
        String errorCode = result.get(1).toString();
        switch (errorCode) {
            case "BID_TOO_LOW":
                String current = result.size() > 2 ? result.get(2).toString() : "current bid";
                throw new BusinessValidationException("Your bid of " + amount + " is lower than or equal to current highest bid of " + current);
            case "AUCTION_NOT_LIVE":
                throw new BusinessValidationException("Auction is not in LIVE status.");
            case "AUCTION_EXPIRED":
                throw new BusinessValidationException("Auction has already expired.");
            case "NOT_INITIALIZED":
                throw new BusinessValidationException("Auction state not initialized.");
            default:
                throw new BusinessValidationException("Bid rejected by sequencer: " + errorCode);
        }
    }

    /**
     * Broadcasts the sequenced bid event across all cluster nodes via Redis Pub/Sub.
     */
    public void broadcastBid(BidUpdateMessage updateMessage) {
        redisTemplate.convertAndSend(RedisConfig.AUCTION_BIDS_TOPIC, updateMessage);
    }

    /**
     * Seeds the Redis Hash for the auction from the database if not present.
     */
    public void ensureAuctionCached(long auctionId, String key) {
        Boolean exists = stringRedisTemplate.hasKey(key);
        if (Boolean.TRUE.equals(exists)) {
            return;
        }

        Auction auction = auctionDAO.findById(auctionId);
        if (auction == null) {
            throw new BusinessValidationException("Auction not found with ID: " + auctionId);
        }

        long endEpochMs = auction.getEndTime()
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli();

        Map<String, String> data = Map.of(
                "status", auction.getStatus() != null ? auction.getStatus() : "LIVE",
                "currentHighestBid", auction.getCurrentHighestBid() != null ? auction.getCurrentHighestBid().toPlainString() : auction.getStartingPrice().toPlainString(),
                "currentHighestBidderId", auction.getCurrentHighestBidderId() != null ? String.valueOf(auction.getCurrentHighestBidderId()) : "0",
                "endTimeMs", String.valueOf(endEpochMs),
                "version", String.valueOf(auction.getVersion())
        );

        stringRedisTemplate.opsForHash().putAll(key, data);
        log.info("[Redis Cache] Initialized in-memory state for Auction #{}", auctionId);
    }
}
