package dev.jayavignesh.auction.dao;

import dev.jayavignesh.auction.model.Auction;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Repository
public class AuctionDAO {

    private final JdbcTemplate jdbcTemplate;

    public AuctionDAO(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Auction> findAll(
            String status,
            String search,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            boolean endingSoon) {
        return findAll(status, search, minPrice, maxPrice, endingSoon, 0, 20);
    }

    public List<Auction> findAll(
            String status,
            String search,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            boolean endingSoon,
            int page,
            int size) {

        StringBuilder sql = new StringBuilder("""
                SELECT
                    id,
                    seller_id,
                    title,
                    description,
                    image_url,
                    starting_price,
                    current_highest_bid,
                    current_highest_bidder_id,
                    start_time,
                    end_time,
                    status,
                    version
                FROM auctions
                WHERE 1 = 1
                """);

        List<Object> parameters = new ArrayList<>();

        if (status != null && !status.isBlank()) {
            sql.append(" AND status = ? ");
            parameters.add(status);
        }

        if (search != null && !search.isBlank()) {
            sql.append("""
                    AND (
                        LOWER(title) LIKE ?
                        OR LOWER(description) LIKE ?
                    )
                    """);

            String searchPattern = "%" + search.trim().toLowerCase() + "%";
            parameters.add(searchPattern);
            parameters.add(searchPattern);
        }

        if (minPrice != null) {
            sql.append(" AND starting_price >= ? ");
            parameters.add(minPrice);
        }

        if (maxPrice != null) {
            sql.append(" AND starting_price <= ? ");
            parameters.add(maxPrice);
        }

        if (endingSoon) {
            sql.append("""
                    AND status = 'LIVE'
                    AND end_time > ?
                    AND end_time <= ?
                    """);
            parameters.add(Timestamp.valueOf(LocalDateTime.now()));
            parameters.add(Timestamp.valueOf(LocalDateTime.now().plusMinutes(30)));
            sql.append(" ORDER BY end_time ASC ");
        } else {
            sql.append(" ORDER BY id ASC ");
        }

        // Enforce pagination to prevent unbounded result sets
        int safePage = Math.max(0, page);
        int safeSize = Math.min(100, Math.max(1, size));
        sql.append(" LIMIT ? OFFSET ? ");
        parameters.add(safeSize);
        parameters.add(safePage * safeSize);

        return jdbcTemplate.query(
                sql.toString(),
                (resultSet, rowNumber) -> mapAuction(resultSet),
                parameters.toArray()
        );
    }

    public Auction findById(long auctionId) {

        String sql = """
                SELECT
                    id,
                    seller_id,
                    title,
                    description,
                    image_url,
                    starting_price,
                    current_highest_bid,
                    current_highest_bidder_id,
                    start_time,
                    end_time,
                    status,
                    version
                FROM auctions
                WHERE id = ?
                """;

        List<Auction> auctions = jdbcTemplate.query(
                sql,
                (resultSet, rowNumber) -> mapAuction(resultSet),
                auctionId
        );

        if (auctions.isEmpty()) {
            return null;
        }

        return auctions.get(0);
    }

    public Auction save(Auction auction) {

        String sql = """
                INSERT INTO auctions (
                    seller_id,
                    title,
                    description,
                    image_url,
                    starting_price,
                    current_highest_bid,
                    current_highest_bidder_id,
                    start_time,
                    end_time,
                    status,
                    version
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;

        KeyHolder keyHolder = new GeneratedKeyHolder();

        jdbcTemplate.update(connection -> {

            PreparedStatement statement = connection.prepareStatement(
                    sql,
                    new String[]{"id"}
            );

            statement.setLong(1, auction.getSellerId());
            statement.setString(2, auction.getTitle());
            statement.setString(3, auction.getDescription());
            statement.setString(4, auction.getImageUrl());
            statement.setBigDecimal(5, auction.getStartingPrice());
            statement.setBigDecimal(6, auction.getCurrentHighestBid());

            if (auction.getCurrentHighestBidderId() == null) {
                statement.setNull(7, java.sql.Types.BIGINT);
            } else {
                statement.setLong(7, auction.getCurrentHighestBidderId());
            }

            statement.setTimestamp(8, Timestamp.valueOf(auction.getStartTime()));
            statement.setTimestamp(9, Timestamp.valueOf(auction.getEndTime()));
            statement.setString(10, auction.getStatus());
            statement.setLong(11, auction.getVersion());

            return statement;

        }, keyHolder);

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
            throw new IllegalStateException("Auction was inserted but no ID was generated.");
        }

        return findById(generatedId.longValue());
    }

    /**
     * Atomically increments auction version, updates current highest bid, and applies anti-sniping.
     * Guaranteed safe against concurrent updates via optimistic locking WHERE version = expectedVersion.
     */
    public boolean updateHighestBidOptimistic(
            long auctionId,
            long expectedVersion,
            BigDecimal newAmount,
            long newBidderId,
            Timestamp snipeThreshold,
            Timestamp extendedEndTime,
            Timestamp nowTimestamp) {

        String updateSql = """
                UPDATE auctions
                SET
                    current_highest_bid = ?,
                    current_highest_bidder_id = ?,
                    end_time = CASE
                                   WHEN end_time <= ? THEN ?
                                   ELSE end_time
                               END,
                    version = version + 1
                WHERE id = ?
                  AND version = ?
                  AND current_highest_bid < ?
                  AND status = 'LIVE'
                  AND end_time > ?
                """;

        int rows = jdbcTemplate.update(
                updateSql,
                newAmount,
                newBidderId,
                snipeThreshold,
                extendedEndTime,
                auctionId,
                expectedVersion,
                newAmount,
                nowTimestamp
        );

        return rows > 0;
    }

    private Auction mapAuction(java.sql.ResultSet resultSet) throws java.sql.SQLException {

        Long highestBidderId = resultSet.getLong("current_highest_bidder_id");
        if (resultSet.wasNull()) {
            highestBidderId = null;
        }

        return new Auction(
                resultSet.getLong("id"),
                resultSet.getLong("seller_id"),
                resultSet.getString("title"),
                resultSet.getString("description"),
                resultSet.getString("image_url"),
                resultSet.getBigDecimal("starting_price"),
                resultSet.getBigDecimal("current_highest_bid"),
                highestBidderId,
                resultSet.getTimestamp("start_time") != null
                        ? resultSet.getTimestamp("start_time").toLocalDateTime()
                        : null,
                resultSet.getTimestamp("end_time") != null
                        ? resultSet.getTimestamp("end_time").toLocalDateTime()
                        : null,
                resultSet.getString("status"),
                resultSet.getLong("version")
        );
    }
}
