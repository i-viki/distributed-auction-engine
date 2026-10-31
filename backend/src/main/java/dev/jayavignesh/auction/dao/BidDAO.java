package dev.jayavignesh.auction.dao;

import dev.jayavignesh.auction.exception.OptimisticLockException;
import dev.jayavignesh.auction.model.Auction;
import dev.jayavignesh.auction.model.Bid;
import dev.jayavignesh.auction.service.NotificationService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public class BidDAO {

        @Value("${auction.anti-snipe.window-minutes:2}")
        private int antiSnipeWindowMinutes = 2;

        @Value("${auction.anti-snipe.extension-minutes:2}")
        private int antiSnipeExtensionMinutes = 2;

        private final JdbcTemplate jdbcTemplate;
        private final AuctionDAO auctionDAO;
        private final UserDAO userDAO;
        private final NotificationService notificationService;

        public BidDAO(
                        JdbcTemplate jdbcTemplate,
                        AuctionDAO auctionDAO,
                        UserDAO userDAO,
                        NotificationService notificationService) {

                this.jdbcTemplate = jdbcTemplate;
                this.auctionDAO = auctionDAO;
                this.userDAO = userDAO;
                this.notificationService = notificationService;
        }

        // ---------------------------------------------------------
        // CHECK USER EXISTS
        // ---------------------------------------------------------

        public boolean userExists(long userId) {
                return userDAO.existsById(userId);
        }

        // ---------------------------------------------------------
        // GET BID HISTORY
        // ---------------------------------------------------------

        public List<Bid> findByAuctionId(long auctionId) {

                String sql = """
                                SELECT
                                    id,
                                    auction_id,
                                    bidder_id,
                                    amount,
                                    placed_at,
                                    auto_bid
                                FROM bids
                                WHERE auction_id = ?
                                ORDER BY placed_at ASC, id ASC
                                """;

                return jdbcTemplate.query(
                                sql,
                                (resultSet, rowNumber) -> mapBid(resultSet),
                                auctionId);
        }

        // ---------------------------------------------------------
        // GET BID BY ID
        // ---------------------------------------------------------

        public Bid findById(long bidId) {

                String sql = """
                                SELECT
                                    id,
                                    auction_id,
                                    bidder_id,
                                    amount,
                                    placed_at,
                                    auto_bid
                                FROM bids
                                WHERE id = ?
                                """;

                List<Bid> bids = jdbcTemplate.query(
                                sql,
                                (resultSet, rowNumber) -> mapBid(resultSet),
                                bidId);

                return bids.isEmpty()
                                ? null
                                : bids.get(0);
        }

        // ---------------------------------------------------------
        // PLACE BID
        // ---------------------------------------------------------

        @Transactional(rollbackFor = Exception.class)
        public Bid placeBid(
                        Bid bid,
                        long expectedVersion,
                        Long previousHighestBidderId) {

                /*
                 * Step 1:
                 * Read the current auction state.
                 */
                Auction auction = auctionDAO.findById(bid.getAuctionId());

                if (auction == null) {
                        throw new IllegalArgumentException(
                                        "Auction not found with ID: "
                                                        + bid.getAuctionId());
                }

                /*
                 * Step 2:
                 * Auction must be LIVE.
                 */
                if (!"LIVE".equals(auction.getStatus())) {
                        throw new IllegalArgumentException(
                                        "Auction is not LIVE.");
                }

                /*
                 * Step 3:
                 * Auction must not have ended.
                 */
                if (auction.getEndTime() == null ||
                                !auction.getEndTime().isAfter(LocalDateTime.now())) {
                        throw new IllegalArgumentException(
                                        "Auction has ended.");
                }

                /*
                 * Step 4:
                 * Optimistic locking check.
                 */
                if (auction.getVersion() != expectedVersion) {
                        throw new OptimisticLockException(
                                        "Auction version changed. Retry the bid.");
                }

                /*
                 * Step 5:
                 * Bid must be higher than the current highest bid.
                 */
                if (bid.getAmount().compareTo(auction.getCurrentHighestBid()) <= 0) {
                        throw new IllegalArgumentException(
                                        "Your bid must be greater than "
                                                        + "the current highest bid of "
                                                        + auction.getCurrentHighestBid()
                                                        + ".");
                }

                /*
                 * Step 6:
                 * Atomically update the auction via AuctionDAO.
                 *
                 * Anti-sniping:
                 * If the bid arrives while 2 minutes or less
                 * remain, extend the auction by 2 minutes.
                 */
                LocalDateTime now = LocalDateTime.now();
                Timestamp snipeThreshold = Timestamp.valueOf(now.plusMinutes(antiSnipeWindowMinutes));
                Timestamp extendedEndTime = Timestamp.valueOf(auction.getEndTime().plusMinutes(antiSnipeExtensionMinutes));
                Timestamp nowTimestamp = Timestamp.valueOf(now);

                boolean updated = auctionDAO.updateHighestBidOptimistic(
                                bid.getAuctionId(),
                                expectedVersion,
                                bid.getAmount(),
                                bid.getBidderId(),
                                snipeThreshold,
                                extendedEndTime,
                                nowTimestamp);

                /*
                 * False means another operation changed the auction first.
                 */
                if (!updated) {
                        throw new OptimisticLockException(
                                        "Concurrent bid conflict detected.");
                }

                /*
                 * Step 7:
                 * Insert successful bid.
                 */
                String insertSql = """
                                INSERT INTO bids (
                                    auction_id,
                                    bidder_id,
                                    amount,
                                    placed_at,
                                    auto_bid
                                )
                                VALUES (?, ?, ?, NOW(), ?)
                                """;

                KeyHolder keyHolder = new GeneratedKeyHolder();

                jdbcTemplate.update(
                                connection -> {
                                        PreparedStatement statement = connection.prepareStatement(
                                                        insertSql,
                                                        new String[] { "id" });

                                        statement.setLong(
                                                        1,
                                                        bid.getAuctionId());

                                        statement.setLong(
                                                        2,
                                                        bid.getBidderId());

                                        statement.setBigDecimal(
                                                        3,
                                                        bid.getAmount());

                                        statement.setBoolean(
                                                        4,
                                                        bid.isAutoBid());

                                        return statement;
                                },
                                keyHolder);

                /*
                 * Step 8:
                 * Get generated bid ID.
                 */
                Number generatedId = null;
                if (keyHolder.getKeys() != null) {
                        Object idVal = keyHolder.getKeys().get("id");
                        if (idVal == null) {
                                idVal = keyHolder.getKeys().get("ID");
                        }
                        if (idVal instanceof Number num) {
                                generatedId = num;
                        }
                }
                if (generatedId == null) {
                        generatedId = keyHolder.getKey();
                }

                if (generatedId == null) {
                        throw new IllegalStateException(
                                        "Bid was inserted but no ID was generated.");
                }

                /*
                 * Step 9:
                 * Retrieve saved bid.
                 */
                Bid savedBid = findById(generatedId.longValue());

                if (savedBid == null) {
                        throw new IllegalStateException(
                                        "Bid was inserted but could not be retrieved.");
                }

                /*
                 * Step 10:
                 * Notify previous highest bidder via NotificationService.
                 */
                if (previousHighestBidderId != null &&
                                previousHighestBidderId != bid.getBidderId()) {
                        notificationService.sendOutbidNotification(
                                        previousHighestBidderId,
                                        bid.getAuctionId());
                }

                return savedBid;
        }

        // ---------------------------------------------------------
        // MAP BID
        // ---------------------------------------------------------

        private Bid mapBid(
                        ResultSet resultSet)
                        throws java.sql.SQLException {

                return new Bid(
                                resultSet.getLong("id"),
                                resultSet.getLong("auction_id"),
                                resultSet.getLong("bidder_id"),
                                resultSet.getBigDecimal("amount"),
                                resultSet.getTimestamp("placed_at") != null
                                                ? resultSet
                                                                .getTimestamp("placed_at")
                                                                .toLocalDateTime()
                                                : null,
                                resultSet.getBoolean("auto_bid"));
        }
}

