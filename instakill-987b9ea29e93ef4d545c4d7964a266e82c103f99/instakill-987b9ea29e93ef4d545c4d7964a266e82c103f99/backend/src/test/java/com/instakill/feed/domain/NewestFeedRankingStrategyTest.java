package com.instakill.feed.domain;

import com.instakill.post.domain.Post;
import com.instakill.post.domain.PostRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Testează strategia „Newest” izolat, fără FeedService. */
class NewestFeedRankingStrategyTest {

    private final PostRepository repository = mock(PostRepository.class);
    private final NewestFeedRankingStrategy strategy = new NewestFeedRankingStrategy();

    @Test
    void handlesNewSort() {
        assertThat(strategy.sort()).isEqualTo(FeedSort.NEW);
    }

    @Test
    void returnsLatestPostsForRequestedPage() {
        List<Post> latest = List.of(post(Instant.now()), post(Instant.now().minusSeconds(60)));
        when(repository.findLatest(2, 5)).thenReturn(latest);

        List<Post> result = strategy.fetch(repository, 2, 5);

        assertThat(result).isEqualTo(latest);
        verify(repository).findLatest(2, 5);
    }

    private Post post(Instant createdAt) {
        return new Post(UUID.randomUUID(), UUID.randomUUID(), "content", null, createdAt, createdAt, 0, 0);
    }
}
