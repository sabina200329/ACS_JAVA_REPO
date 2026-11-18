package com.instakill.feed.domain;

import com.instakill.post.domain.Post;
import com.instakill.post.domain.PostRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Testează strategia „Trending” izolat, fără FeedService. */
class TrendingFeedRankingStrategyTest {

    private final PostRepository repository = mock(PostRepository.class);
    private final TrendingFeedRankingStrategy strategy = new TrendingFeedRankingStrategy();
    private final Instant now = Instant.parse("2026-11-18T12:00:00Z");

    @Test
    void handlesTrendingSort() {
        assertThat(strategy.sort()).isEqualTo(FeedSort.TRENDING);
    }

    @Test
    void ordersByLikesThenCommentsThenNewest() {
        Post older = post(now.minusSeconds(300), 5, 2);
        Post newer = post(now.minusSeconds(100), 5, 2);
        Post mostComments = post(now.minusSeconds(200), 5, 9);
        Post mostLikes = post(now.minusSeconds(400), 10, 0);
        Post least = post(now, 1, 1);
        when(repository.findLatest(eq(0), anyInt()))
                .thenReturn(List.of(least, older, newer, mostComments, mostLikes));

        List<Post> result = strategy.fetch(repository, 0, 10);

        assertThat(result).containsExactly(mostLikes, mostComments, newer, older, least);
    }

    @Test
    void paginatesAfterSorting() {
        List<Post> candidates = List.of(
                post(now, 1, 0),
                post(now, 4, 0),
                post(now, 3, 0),
                post(now, 2, 0));
        when(repository.findLatest(eq(0), anyInt())).thenReturn(candidates);

        List<Post> secondPage = strategy.fetch(repository, 1, 2);

        assertThat(secondPage)
                .extracting(Post::likeCount)
                .containsExactly(2L, 1L);
    }

    @Test
    void returnsEmptyListWhenNoPosts() {
        when(repository.findLatest(eq(0), anyInt())).thenReturn(List.of());

        assertThat(strategy.fetch(repository, 0, 5)).isEmpty();
    }

    @Test
    void fetchesEnoughCandidatesFromRepository() {
        when(repository.findLatest(0, 20)).thenReturn(List.of(post(now, 7, 3)));

        // size 10 => fetchSize = max(20, 10) = 20 candidați pentru pagina 0
        List<Post> result = strategy.fetch(repository, 0, 10);

        assertThat(result).extracting(Post::likeCount, Post::commentCount).containsExactly(tuple(7L, 3L));
    }

    private Post post(Instant createdAt, long likes, long comments) {
        return new Post(UUID.randomUUID(), UUID.randomUUID(), "content", null, createdAt, createdAt, likes, comments);
    }
}
