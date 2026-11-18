package com.instakill.analytics.application;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Statisticile pentru utilizatorul logat (Lab 5, partea 1).
 */
public record UserAnalytics(
        List<WeeklyStats> weekly,
        List<TopPost> topPosts,
        LikeCommentRatio likeCommentRatio,
        List<MediaTypeStats> byMediaType,
        EngagementRank engagementRank
) {

    /** 1. Postări, like-uri și comentarii grupate după săptămâna creării postării. */
    public record WeeklyStats(LocalDate weekStart, long posts, long likes, long comments) {
    }

    /** 2. Top 5 postări după engagement = like-uri + comentarii. */
    public record TopPost(UUID id, String content, String imageUrl, Instant createdAt,
                          long likes, long comments, long engagement) {
    }

    /** 3. Raportul like-uri / comentarii pe toate postările (null dacă nu există comentarii). */
    public record LikeCommentRatio(long likes, long comments, Double ratio) {
    }

    /** 4. Media like-urilor pe postare, pe tip de media (photo / video / text). */
    public record MediaTypeStats(String mediaType, long posts, double averageLikes) {
    }

    /**
     * 5. Poziția utilizatorului după engagement mediu pe postare, între toți utilizatorii cu postări.
     * topPercent = 5.0 înseamnă „ești în top 5%”. Null dacă utilizatorul nu are postări.
     */
    public record EngagementRank(double averageEngagement, long rank, long totalUsers, double topPercent) {
    }
}
