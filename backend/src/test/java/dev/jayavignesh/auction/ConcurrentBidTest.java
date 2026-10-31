package dev.jayavignesh.auction;

import dev.jayavignesh.auction.dao.BidDAO;
import dev.jayavignesh.auction.exception.OptimisticLockException;
import dev.jayavignesh.auction.model.Bid;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

@SpringBootTest
class ConcurrentBidTest {

    @Autowired
    private BidDAO bidDAO;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void testConcurrentBids() throws Exception {

        System.out.println();
        System.out.println("======================================");
        System.out.println("LIVEBID CONCURRENT BID TEST");
        System.out.println("======================================");

        /*
         * Prepare seed data for users and auction #1.
         */
        jdbcTemplate.update("DELETE FROM bids WHERE auction_id = 1");
        jdbcTemplate.update("DELETE FROM auctions WHERE id = 1");
        jdbcTemplate.update("DELETE FROM users WHERE id IN (1, 2, 3)");

        jdbcTemplate.update("INSERT INTO users (id, name, email, password_hash, role, created_at) VALUES (1, 'seller', 'seller@test.com', 'pwd', 'USER', NOW())");
        jdbcTemplate.update("INSERT INTO users (id, name, email, password_hash, role, created_at) VALUES (2, 'user2', 'user2@test.com', 'pwd', 'USER', NOW())");
        jdbcTemplate.update("INSERT INTO users (id, name, email, password_hash, role, created_at) VALUES (3, 'user3', 'user3@test.com', 'pwd', 'USER', NOW())");

        jdbcTemplate.update("""
                INSERT INTO auctions (id, seller_id, title, description, starting_price, current_highest_bid, current_highest_bidder_id, start_time, end_time, status, version)
                VALUES (1, 1, 'Vintage Watch', 'Mint condition', 10000.00, 39000.00, 2, TIMESTAMPADD(HOUR, -1, NOW()), TIMESTAMPADD(MINUTE, 30, NOW()), 'LIVE', 15)
                """);

        System.out.println("Auction prepared.");
        System.out.println("Current highest bid: ₹39000.00");
        System.out.println("Current highest bidder: User #2");
        System.out.println("Version: 15");
        System.out.println();

        /*
         * Both bidders will try the exact same amount.
         */
        BigDecimal concurrentBidAmount =
                new BigDecimal("40000.00");

        long auctionId = 1;
        long expectedVersion = 15;

        /*
         * Both threads must start together.
         */
        CountDownLatch startLatch =
                new CountDownLatch(1);

        ExecutorService executor =
                Executors.newFixedThreadPool(2);

        List<Future<String>> results =
                new ArrayList<>();

        /*
         * User #2
         */
        results.add(
                executor.submit(() -> {

                    Bid bid = new Bid(
                            auctionId,
                            2,
                            concurrentBidAmount,
                            false
                    );

                    startLatch.await();

                    try {

                        Bid savedBid =
                                bidDAO.placeBid(
                                        bid,
                                        expectedVersion,
                                        2L
                                );

                        return "[User #2] SUCCESS - "
                                + "Bid ID: "
                                + savedBid.getId()
                                + ", Amount: ₹"
                                + savedBid.getAmount();

                    } catch (OptimisticLockException exception) {

                        return "[User #2] FAILED - "
                                + exception.getMessage();
                    }
                })
        );

        /*
         * User #3
         */
        results.add(
                executor.submit(() -> {

                    Bid bid = new Bid(
                            auctionId,
                            3,
                            concurrentBidAmount,
                            false
                    );

                    startLatch.await();

                    try {

                        Bid savedBid =
                                bidDAO.placeBid(
                                        bid,
                                        expectedVersion,
                                        3L
                                );

                        return "[User #3] SUCCESS - "
                                + "Bid ID: "
                                + savedBid.getId()
                                + ", Amount: ₹"
                                + savedBid.getAmount();

                    } catch (OptimisticLockException exception) {

                        return "[User #3] FAILED - "
                                + exception.getMessage();
                    }
                })
        );

        /*
         * Release both threads at approximately
         * the same time.
         */
        System.out.println(
                "Starting both concurrent bidders..."
        );

        startLatch.countDown();

        /*
         * Wait for both requests to finish.
         */
        for (Future<String> result : results) {
            System.out.println(result.get());
        }

        executor.shutdown();

        /*
         * Read final auction state.
         */
        AuctionState finalAuction =
                jdbcTemplate.queryForObject(
                        """
                        SELECT
                            current_highest_bid,
                            current_highest_bidder_id,
                            version
                        FROM auctions
                        WHERE id = 1
                        """,
                        (resultSet, rowNumber) ->
                                new AuctionState(
                                        resultSet.getBigDecimal(
                                                "current_highest_bid"
                                        ),
                                        resultSet.getLong(
                                                "current_highest_bidder_id"
                                        ),
                                        resultSet.getLong(
                                                "version"
                                        )
                                )
                );

        System.out.println();
        System.out.println("FINAL AUCTION STATE:");
        System.out.println(
                "Current highest bid: ₹"
                        + finalAuction.currentHighestBid()
        );
        System.out.println(
                "Current highest bidder: User #"
                        + finalAuction.currentHighestBidderId()
        );
        System.out.println(
                "Version: "
                        + finalAuction.version()
        );

        /*
         * Count successful 40000 bids.
         */
        Integer successfulBids =
                jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(*)
                        FROM bids
                        WHERE auction_id = ?
                          AND amount = ?
                        """,
                        Integer.class,
                        auctionId,
                        concurrentBidAmount
                );

        System.out.println();
        System.out.println(
                "Number of ₹40000 bids inserted: "
                        + successfulBids
        );

        /*
         * The important assertions.
         */
        if (successfulBids != 1) {
            throw new AssertionError(
                    "Concurrency test failed: "
                            + "expected exactly 1 successful bid, "
                            + "but found "
                            + successfulBids
            );
        }

        if (finalAuction.version() != 16) {
            throw new AssertionError(
                    "Concurrency test failed: "
                            + "expected version 16, "
                            + "but found "
                            + finalAuction.version()
            );
        }

        if (finalAuction.currentHighestBid()
                .compareTo(concurrentBidAmount) != 0) {

            throw new AssertionError(
                    "Concurrency test failed: "
                            + "highest bid is not ₹40000."
            );
        }

        System.out.println();
        System.out.println(
                "======================================"
        );
        System.out.println(
                "CONCURRENCY TEST PASSED"
        );
        System.out.println(
                "Only one concurrent bid succeeded."
        );
        System.out.println(
                "Optimistic locking prevented the"
        );
        System.out.println(
                "second bidder from overwriting it."
        );
        System.out.println(
                "======================================"
        );
    }

    private record AuctionState(
            BigDecimal currentHighestBid,
            long currentHighestBidderId,
            long version) {
    }
}