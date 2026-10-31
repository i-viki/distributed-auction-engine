package dev.jayavignesh.auction.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/**
 * Background Scheduler for Auction Lifecycle Management:
 * 1. Automatically activates SCHEDULED auctions to LIVE once start_time arrives.
 * 2. Closes LIVE auctions once end_time expires and generates winner notifications.
 */
@Component
public class AuctionClosingScheduler {

    private static final Logger log = LoggerFactory.getLogger(AuctionClosingScheduler.class);

    private final JdbcTemplate jdbcTemplate;
    private final NotificationService notificationService;

    @Value("${auction.scheduler.batch-size:100}")
    private int batchSize;

    public AuctionClosingScheduler(
            JdbcTemplate jdbcTemplate,
            NotificationService notificationService) {

        this.jdbcTemplate = jdbcTemplate;
        this.notificationService = notificationService;
    }

    @Scheduled(fixedRateString = "${auction.scheduler.rate-ms:10000}")
    public void updateAuctionStatuses() {

        // ---------------------------------------------------------
        // STEP 1: SCHEDULED -> LIVE (Batch Update)
        // ---------------------------------------------------------
        String activateSql = """
                UPDATE auctions
                SET status = 'LIVE'
                WHERE status = 'SCHEDULED'
                  AND start_time <= NOW()
                  AND end_time > NOW()
                """;

        int activatedCount = jdbcTemplate.update(activateSql);
        if (activatedCount > 0) {
            log.info("Activated {} scheduled auction(s) to LIVE status.", activatedCount);
        }

        // ---------------------------------------------------------
        // STEP 2: LIVE -> CLOSED (Bounded query with LIMIT to prevent OOM)
        // ---------------------------------------------------------
        String findExpiredSql = """
                SELECT
                    id,
                    current_highest_bidder_id
                FROM auctions
                WHERE status = 'LIVE'
                  AND end_time <= NOW()
                ORDER BY end_time ASC
                LIMIT ?
                """;

        List<ExpiredAuction> expiredAuctions = jdbcTemplate.query(
                findExpiredSql,
                (resultSet, rowNumber) ->
                        new ExpiredAuction(
                                resultSet.getLong("id"),
                                getNullableLong(resultSet, "current_highest_bidder_id")
                        ),
                batchSize
        );

        for (ExpiredAuction auction : expiredAuctions) {
            try {
                closeSingleAuction(auction);
            } catch (Exception ex) {
                log.error("Failed to close auction ID {}: {}", auction.auctionId(), ex.getMessage());
            }
        }
    }

    /**
     * Closes an individual auction within an isolated atomic transaction.
     * Prevents long-running monolithic transactions across multiple auctions.
     */
    @Transactional
    public void closeSingleAuction(ExpiredAuction auction) {

        String closeSql = """
                UPDATE auctions
                SET status = 'CLOSED'
                WHERE id = ?
                  AND status = 'LIVE'
                  AND end_time <= NOW()
                """;

        int rowsUpdated = jdbcTemplate.update(closeSql, auction.auctionId());

        /*
         * Only the worker that successfully changed LIVE -> CLOSED
         * creates the winner notification.
         */
        if (rowsUpdated == 1) {
            log.info("Closed expired auction ID {}.", auction.auctionId());

            if (auction.highestBidderId() != null) {
                notificationService.sendAuctionWonNotification(
                        auction.highestBidderId(),
                        auction.auctionId()
                );
            }
        }
    }

    private Long getNullableLong(
            ResultSet resultSet,
            String columnName)
            throws SQLException {

        long value = resultSet.getLong(columnName);

        if (resultSet.wasNull()) {
            return null;
        }

        return value;
    }

    private record ExpiredAuction(
            long auctionId,
            Long highestBidderId) {
    }
}

