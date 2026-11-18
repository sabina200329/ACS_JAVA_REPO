package com.instakill.analytics.application;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;

/**
 * Interogările de analytics diferă puțin între baze de date
 * (funcții de dată și felul în care e stocat created_at).
 * Fiecare implementare conține SQL-ul pentru o bază de date.
 */
interface AnalyticsSql {

    String weekly();

    String topPosts();

    String ratio();

    String byMediaType();

    String rank();

    Instant createdAt(ResultSet rs, String column) throws SQLException;

    // Părțile comune (funcționează la fel în SQLite și PostgreSQL)

    String MEDIA_TYPE_CASE = """
            CASE
                WHEN p.image_url IS NULL OR TRIM(p.image_url) = '' THEN 'text'
                WHEN LOWER(p.image_url) LIKE '%.mp4'
                  OR LOWER(p.image_url) LIKE '%.mov'
                  OR LOWER(p.image_url) LIKE '%.webm' THEN 'video'
                ELSE 'photo'
            END""";

    String TOP_POSTS = """
            SELECT p.id, p.content, p.image_url, p.created_at, p.like_count, p.comment_count,
                   (p.like_count + p.comment_count) AS engagement
            FROM posts p
            WHERE p.author_id = :userId
            ORDER BY engagement DESC, p.created_at DESC
            LIMIT 5
            """;

    String RATIO = """
            SELECT COALESCE(SUM(p.like_count), 0)    AS likes,
                   COALESCE(SUM(p.comment_count), 0) AS comments,
                   CASE WHEN COALESCE(SUM(p.comment_count), 0) = 0 THEN NULL
                        ELSE ROUND(1.0 * SUM(p.like_count) / SUM(p.comment_count), 2) END AS ratio
            FROM posts p
            WHERE p.author_id = :userId
            """;

    String BY_MEDIA_TYPE = "SELECT " + MEDIA_TYPE_CASE + """
             AS media_type,
                   COUNT(*)                    AS posts,
                   ROUND(AVG(p.like_count), 2) AS avg_likes
            FROM posts p
            WHERE p.author_id = :userId
            GROUP BY 1
            ORDER BY 1
            """;

    String RANK = """
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
            SELECT ROUND(avg_engagement, 2)            AS avg_engagement,
                   rnk,
                   total_users,
                   ROUND(100.0 * rnk / total_users, 2) AS top_percent
            FROM ranked
            WHERE author_id = :userId
            """;

    /** SQLite: created_at e stocat ca epoch în milisecunde. */
    final class Sqlite implements AnalyticsSql {
        @Override
        public String weekly() {
            return """
                    SELECT date(p.created_at / 1000, 'unixepoch', 'weekday 0', '-6 days') AS week_start,
                           COUNT(*)                          AS posts,
                           COALESCE(SUM(p.like_count), 0)    AS likes,
                           COALESCE(SUM(p.comment_count), 0) AS comments
                    FROM posts p
                    WHERE p.author_id = :userId
                    GROUP BY week_start
                    ORDER BY week_start DESC
                    """;
        }

        @Override public String topPosts() { return TOP_POSTS; }
        @Override public String ratio() { return RATIO; }
        @Override public String byMediaType() { return BY_MEDIA_TYPE; }
        @Override public String rank() { return RANK; }

        @Override
        public Instant createdAt(ResultSet rs, String column) throws SQLException {
            return Instant.ofEpochMilli(rs.getLong(column));
        }
    }

    /** PostgreSQL: created_at e de tip timestamp with time zone. */
    final class Postgres implements AnalyticsSql {
        @Override
        public String weekly() {
            return """
                    SELECT CAST(date_trunc('week', p.created_at AT TIME ZONE 'UTC') AS date) AS week_start,
                           COUNT(*)                          AS posts,
                           COALESCE(SUM(p.like_count), 0)    AS likes,
                           COALESCE(SUM(p.comment_count), 0) AS comments
                    FROM posts p
                    WHERE p.author_id = :userId
                    GROUP BY 1
                    ORDER BY 1 DESC
                    """;
        }

        @Override public String topPosts() { return TOP_POSTS; }
        @Override public String ratio() { return RATIO; }
        @Override public String byMediaType() { return BY_MEDIA_TYPE; }
        @Override public String rank() { return RANK; }

        @Override
        public Instant createdAt(ResultSet rs, String column) throws SQLException {
            return rs.getTimestamp(column).toInstant();
        }
    }
}
