package dev.jayavignesh.auction.dao;

import dev.jayavignesh.auction.model.User;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public class UserDAO {

    private final JdbcTemplate jdbcTemplate;

    public UserDAO(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public User save(User user) {

        String sql = """
                INSERT INTO users (
                    name,
                    email,
                    password_hash
                )
                VALUES (?, ?, ?)
                """;

        KeyHolder keyHolder = new GeneratedKeyHolder();

        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    sql,
                    new String[]{"id"}
            );
            ps.setString(1, user.getName());
            ps.setString(2, user.getEmail());
            ps.setString(3, user.getPasswordHash());
            return ps;
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
            throw new IllegalStateException(
                    "User was inserted but no ID was generated."
            );
        }

        user.setId(generatedId.longValue());
        if (user.getCreatedAt() == null) {
            user.setCreatedAt(LocalDateTime.now());
        }

        return user;
    }

    public User findById(long id) {

        String sql = """
                SELECT
                    id,
                    name,
                    email,
                    password_hash,
                    created_at
                FROM users
                WHERE id = ?
                """;

        List<User> users = jdbcTemplate.query(
                sql,
                (resultSet, rowNumber) -> mapUser(resultSet),
                id
        );

        return users.isEmpty() ? null : users.get(0);
    }

    public boolean existsById(long id) {
        String sql = """
                SELECT COUNT(*)
                FROM users
                WHERE id = ?
                """;

        Integer count = jdbcTemplate.queryForObject(
                sql,
                Integer.class,
                id
        );

        return count != null && count > 0;
    }

    public User findByEmail(String email) {

        String sql = """
                SELECT
                    id,
                    name,
                    email,
                    password_hash,
                    created_at
                FROM users
                WHERE email = ?
                """;

        List<User> users = jdbcTemplate.query(
                sql,
                (resultSet, rowNumber) -> mapUser(resultSet),
                email
        );

        return users.isEmpty() ? null : users.get(0);
    }

    private User mapUser(ResultSet resultSet)
            throws SQLException {

        return new User(
                resultSet.getLong("id"),
                resultSet.getString("name"),
                resultSet.getString("email"),
                resultSet.getString("password_hash"),
                resultSet.getTimestamp("created_at") != null
                        ? resultSet
                        .getTimestamp("created_at")
                        .toLocalDateTime()
                        : null
        );
    }
}
