package com.instakill.analytics.application;

import com.instakill.analytics.application.UserAnalytics.EngagementRank;
import com.instakill.analytics.application.UserAnalytics.LikeCommentRatio;
import com.instakill.analytics.application.UserAnalytics.MediaTypeStats;
import com.instakill.analytics.application.UserAnalytics.TopPost;
import com.instakill.analytics.application.UserAnalytics.WeeklyStats;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Toate statisticile sunt calculate direct în baza de date cu interogări SQL
 * (agregări, GROUP BY, funcții fereastră), nu în Java, pentru performanță.
 *
 * Folosim contoarele denormalizate posts.like_count / posts.comment_count,
 * care sunt actualizate de InteractionService (și de scriptul populate_db.py),
 * ca să evităm JOIN-uri pe tabelele mari likes / comments.
 *
 * SQL-ul specific bazei de date (SQLite sau PostgreSQL) e ales la pornire, vezi {@link AnalyticsSql}.
 */
@Service
@Transactional(readOnly = true)
public class AnalyticsService {

    private final NamedParameterJdbcTemplate jdbc;
    private final AnalyticsSql sql;

    public AnalyticsService(NamedParameterJdbcTemplate jdbc, DataSource dataSource) {
        this.jdbc = jdbc;
        this.sql = detect(dataSource);
    }

    static AnalyticsSql detect(DataSource dataSource) {
        try (Connection connection = dataSource.getConnection()) {
            String product = connection.getMetaData().getDatabaseProductName();
            return product != null && product.toLowerCase().contains("postgres")
                    ? new AnalyticsSql.Postgres()
                    : new AnalyticsSql.Sqlite();
        } catch (SQLException e) {
            throw new IllegalStateException("Nu pot determina tipul bazei de date", e);
        }
    }

    public UserAnalytics forUser(UUID userId) {
        MapSqlParameterSource params = new MapSqlParameterSource("userId", userId.toString());
        return new UserAnalytics(
                weekly(params),
                topPosts(params),
                ratio(params),
                byMediaType(params),
                rank(params)
        );
    }

    private List<WeeklyStats> weekly(MapSqlParameterSource params) {
        return jdbc.query(sql.weekly(), params, (rs, i) -> new WeeklyStats(
                LocalDate.parse(rs.getString("week_start")),
                rs.getLong("posts"),
                rs.getLong("likes"),
                rs.getLong("comments")));
    }

    private List<TopPost> topPosts(MapSqlParameterSource params) {
        return jdbc.query(sql.topPosts(), params, (rs, i) -> new TopPost(
                UUID.fromString(rs.getString("id").trim()),
                rs.getString("content"),
                rs.getString("image_url"),
                sql.createdAt(rs, "created_at"),
                rs.getLong("like_count"),
                rs.getLong("comment_count"),
                rs.getLong("engagement")));
    }

    private LikeCommentRatio ratio(MapSqlParameterSource params) {
        return jdbc.queryForObject(sql.ratio(), params, (rs, i) -> {
            Object ratio = rs.getObject("ratio");
            return new LikeCommentRatio(rs.getLong("likes"), rs.getLong("comments"),
                    ratio instanceof Number n ? n.doubleValue() : null);
        });
    }

    private List<MediaTypeStats> byMediaType(MapSqlParameterSource params) {
        return jdbc.query(sql.byMediaType(), params, (rs, i) -> new MediaTypeStats(
                rs.getString("media_type"),
                rs.getLong("posts"),
                rs.getDouble("avg_likes")));
    }

    private EngagementRank rank(MapSqlParameterSource params) {
        List<EngagementRank> rows = jdbc.query(sql.rank(), params, (rs, i) -> new EngagementRank(
                rs.getDouble("avg_engagement"),
                rs.getLong("rnk"),
                rs.getLong("total_users"),
                rs.getDouble("top_percent")));
        return rows.isEmpty() ? null : rows.getFirst();
    }
}
