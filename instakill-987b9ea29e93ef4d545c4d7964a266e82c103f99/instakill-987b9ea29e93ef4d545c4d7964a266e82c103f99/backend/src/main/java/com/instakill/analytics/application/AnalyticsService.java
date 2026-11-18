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

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Toate statisticile sunt calculate direct în SQLite cu interogări SQL
 * (agregări, GROUP BY, funcții fereastră), nu în Java, pentru performanță.
 *
 * Folosim contoarele denormalizate posts.like_count / posts.comment_count,
 * care sunt actualizate de InteractionService (și de scriptul populate_db.py),
 * ca să evităm JOIN-uri pe tabelele mari likes / comments.
 *
 * Datele (created_at) sunt stocate ca epoch în milisecunde.
 */
@Service
@Transactional(readOnly = true)
public class AnalyticsService {

    static final String WEEKLY_SQL = """
            SELECT date(p.created_at / 1000, 'unixepoch', 'weekday 0', '-6 days') AS week_start,
                   COUNT(*)                    AS posts,
                   COALESCE(SUM(p.like_count), 0)    AS likes,
                   COALESCE(SUM(p.comment_count), 0) AS comments
            FROM posts p
            WHERE p.author_id = :userId
            GROUP BY week_start
            ORDER BY week_start DESC
            """;

    static final String TOP_POSTS_SQL = """
            SELECT p.id, p.content, p.image_url, p.created_at, p.like_count, p.comment_count,
                   (p.like_count + p.comment_count) AS engagement
            FROM posts p
            WHERE p.author_id = :userId
            ORDER BY engagement DESC, p.created_at DESC
            LIMIT 5
            """;

    static final String RATIO_SQL = """
            SELECT COALESCE(SUM(p.like_count), 0)    AS likes,
                   COALESCE(SUM(p.comment_count), 0) AS comments,
                   CASE WHEN COALESCE(SUM(p.comment_count), 0) = 0 THEN NULL
                        ELSE ROUND(1.0 * SUM(p.like_count) / SUM(p.comment_count), 2) END AS ratio
            FROM posts p
            WHERE p.author_id = :userId
            """;

    static final String MEDIA_TYPE_SQL = """
            SELECT CASE
                       WHEN p.image_url IS NULL OR TRIM(p.image_url) = '' THEN 'text'
                       WHEN LOWER(p.image_url) LIKE '%.mp4'
                         OR LOWER(p.image_url) LIKE '%.mov'
                         OR LOWER(p.image_url) LIKE '%.webm' THEN 'video'
                       ELSE 'photo'
                   END                              AS media_type,
                   COUNT(*)                         AS posts,
                   ROUND(AVG(p.like_count), 2)      AS avg_likes
            FROM posts p
            WHERE p.author_id = :userId
            GROUP BY media_type
            ORDER BY media_type
            """;

    static final String RANK_SQL = """
            WITH user_avg AS (
                SELECT p.author_id, AVG(p.like_count + p.comment_count) AS avg_engagement
                FROM posts p
                GROUP BY p.author_id
            ),
            ranked AS (
                SELECT author_id,
                       avg_engagement,
                       RANK()   OVER (ORDER BY avg_engagement DESC) AS rnk,
                       COUNT(*) OVER ()                             AS total_users
                FROM user_avg
            )
            SELECT ROUND(avg_engagement, 2)               AS avg_engagement,
                   rnk,
                   total_users,
                   ROUND(100.0 * rnk / total_users, 2)    AS top_percent
            FROM ranked
            WHERE author_id = :userId
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public AnalyticsService(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
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
        return jdbc.query(WEEKLY_SQL, params, (rs, i) -> new WeeklyStats(
                LocalDate.parse(rs.getString("week_start")),
                rs.getLong("posts"),
                rs.getLong("likes"),
                rs.getLong("comments")));
    }

    private List<TopPost> topPosts(MapSqlParameterSource params) {
        return jdbc.query(TOP_POSTS_SQL, params, (rs, i) -> new TopPost(
                UUID.fromString(rs.getString("id")),
                rs.getString("content"),
                rs.getString("image_url"),
                Instant.ofEpochMilli(rs.getLong("created_at")),
                rs.getLong("like_count"),
                rs.getLong("comment_count"),
                rs.getLong("engagement")));
    }

    private LikeCommentRatio ratio(MapSqlParameterSource params) {
        return jdbc.queryForObject(RATIO_SQL, params, (rs, i) -> {
            Object ratio = rs.getObject("ratio");
            return new LikeCommentRatio(rs.getLong("likes"), rs.getLong("comments"),
                    ratio instanceof Number n ? n.doubleValue() : null);
        });
    }

    private List<MediaTypeStats> byMediaType(MapSqlParameterSource params) {
        return jdbc.query(MEDIA_TYPE_SQL, params, (rs, i) -> new MediaTypeStats(
                rs.getString("media_type"),
                rs.getLong("posts"),
                rs.getDouble("avg_likes")));
    }

    private EngagementRank rank(MapSqlParameterSource params) {
        List<EngagementRank> rows = jdbc.query(RANK_SQL, params, (rs, i) -> new EngagementRank(
                rs.getDouble("avg_engagement"),
                rs.getLong("rnk"),
                rs.getLong("total_users"),
                rs.getDouble("top_percent")));
        return rows.isEmpty() ? null : rows.getFirst();
    }
}
