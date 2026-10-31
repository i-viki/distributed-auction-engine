package dev.jayavignesh.auction.dao;

import dev.jayavignesh.auction.model.AutoBid;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;

@Repository
public class AutoBidDAO {

    private final JdbcTemplate jdbcTemplate;

    public AutoBidDAO(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // ---------------------------------------------------------
    // FIND USER'S AUTO-BID FOR AN AUCTION
    // ---------------------------------------------------------

    public AutoBid findByAuctionAndUser(
            long auctionId,
            long userId) {

        String sql = """
                SELECT
                    id,
                    auction_id,
                    user_id,
                    max_amount,
                    active
                FROM auto_bid_settings
                WHERE auction_id = ?
                  AND user_id = ?
                """;

        List<AutoBid> autoBids = jdbcTemplate.query(
                sql,
                (resultSet, rowNumber) ->
                        mapAutoBid(resultSet),
                auctionId,
                userId
        );

        return autoBids.isEmpty()
                ? null
                : autoBids.get(0);
    }

    // ---------------------------------------------------------
    // FIND ALL ACTIVE AUTO-BIDS FOR AN AUCTION
    // ---------------------------------------------------------

    public List<AutoBid> findActiveByAuctionId(
            long auctionId) {

        String sql = """
                SELECT
                    id,
                    auction_id,
                    user_id,
                    max_amount,
                    active
                FROM auto_bid_settings
                WHERE auction_id = ?
                  AND active = TRUE
                ORDER BY id ASC
                """;

        return jdbcTemplate.query(
                sql,
                (resultSet, rowNumber) ->
                        mapAutoBid(resultSet),
                auctionId
        );
    }

    // ---------------------------------------------------------
    // SAVE / UPDATE AUTO-BID
    // ---------------------------------------------------------

    public AutoBid save(
            AutoBid autoBid) {

        AutoBid existing =
                findByAuctionAndUser(
                        autoBid.getAuctionId(),
                        autoBid.getUserId()
                );

        if (existing != null) {

            String updateSql = """
                    UPDATE auto_bid_settings
                    SET
                        max_amount = ?,
                        active = ?
                    WHERE id = ?
                    """;

            jdbcTemplate.update(
                    updateSql,
                    autoBid.getMaxAmount(),
                    autoBid.isActive(),
                    existing.getId()
            );

            autoBid.setId(existing.getId());

            return autoBid;
        }

        String insertSql = """
                INSERT INTO auto_bid_settings (
                    auction_id,
                    user_id,
                    max_amount,
                    active
                )
                VALUES (?, ?, ?, ?)
                """;

        KeyHolder keyHolder =
                new GeneratedKeyHolder();

        jdbcTemplate.update(
                connection -> {

                    PreparedStatement statement =
                            connection.prepareStatement(
                                    insertSql,
                                    new String[]{"id"}
                            );

                    statement.setLong(
                            1,
                            autoBid.getAuctionId()
                    );

                    statement.setLong(
                            2,
                            autoBid.getUserId()
                    );

                    statement.setBigDecimal(
                            3,
                            autoBid.getMaxAmount()
                    );

                    statement.setBoolean(
                            4,
                            autoBid.isActive()
                    );

                    return statement;

                },
                keyHolder
        );

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
                    "Auto-bid was inserted but no ID was generated."
            );
        }

        autoBid.setId(
                generatedId.longValue()
        );

        return autoBid;
    }

    // ---------------------------------------------------------
    // DEACTIVATE AUTO-BID
    // ---------------------------------------------------------

    public boolean deactivate(
            long auctionId,
            long userId) {

        String sql = """
                UPDATE auto_bid_settings
                SET active = FALSE
                WHERE auction_id = ?
                  AND user_id = ?
                  AND active = TRUE
                """;

        return jdbcTemplate.update(
                sql,
                auctionId,
                userId
        ) > 0;
    }

    // ---------------------------------------------------------
    // MAP DATABASE ROW
    // ---------------------------------------------------------

    private AutoBid mapAutoBid(
            java.sql.ResultSet resultSet)
            throws java.sql.SQLException {

        return new AutoBid(
                resultSet.getLong("id"),
                resultSet.getLong("auction_id"),
                resultSet.getLong("user_id"),
                resultSet.getBigDecimal("max_amount"),
                resultSet.getBoolean("active")
        );
    }
}
