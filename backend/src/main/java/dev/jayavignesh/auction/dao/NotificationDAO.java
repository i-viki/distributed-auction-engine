package dev.jayavignesh.auction.dao;

import dev.jayavignesh.auction.dto.NotificationResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public class NotificationDAO {

    private final JdbcTemplate jdbcTemplate;

    public NotificationDAO(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void createNotification(
            long userId,
            long auctionId,
            String type) {

        String sql = """
                INSERT INTO notifications (
                    user_id,
                    auction_id,
                    type
                )
                VALUES (?, ?, ?)
                """;

        jdbcTemplate.update(
                sql,
                userId,
                auctionId,
                type
        );
    }

    public boolean existsSince(
            long userId,
            long auctionId,
            String type,
            LocalDateTime since) {

        String sql = """
                SELECT COUNT(*)
                FROM notifications
                WHERE user_id = ?
                  AND auction_id = ?
                  AND type = ?
                  AND sent_at >= ?
                """;

        Integer count = jdbcTemplate.queryForObject(
                sql,
                Integer.class,
                userId,
                auctionId,
                type,
                java.sql.Timestamp.valueOf(since)
        );

        return count != null && count > 0;
    }

    public List<NotificationResponse> findByUserId(long userId) {
        String sql = """
            SELECT
                n.id,
                n.user_id,
                n.auction_id,
                a.title AS auction_title,
                n.type,
                n.sent_at
            FROM notifications n
            LEFT JOIN auctions a ON a.id = n.auction_id
            WHERE n.user_id = ?
            ORDER BY n.sent_at DESC
            """;

        return jdbcTemplate.query(
                sql,
                (rs, rowNum) -> mapNotification(rs),
                userId
        );
    }

    private NotificationResponse mapNotification(ResultSet rs) throws SQLException {
        return new NotificationResponse(
                rs.getLong("id"),
                rs.getLong("user_id"),
                rs.getLong("auction_id"),
                rs.getString("auction_title"),
                rs.getString("type"),
                rs.getTimestamp("sent_at") != null
                        ? rs.getTimestamp("sent_at").toLocalDateTime()
                        : null
        );
    }
}
