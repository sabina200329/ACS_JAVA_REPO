package com.instakill.interaction.application;

import com.instakill.common.clock.Clock;
import com.instakill.common.id.IdGenerator;
import com.instakill.interaction.domain.CommentRepository;
import com.instakill.interaction.domain.Like;
import com.instakill.interaction.domain.LikeRepository;
import com.instakill.interaction.domain.NoOpDomainEventPublisher;
import com.instakill.post.domain.Post;
import com.instakill.post.domain.PostRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * InteractionService folosit cu publisher-ul real NoOpDomainEventPublisher (Null Object):
 * like-urile funcționează normal, fără excepții și fără verificări de null.
 */
class InteractionServiceNoOpPublisherTest {

    private final LikeRepository likes = mock(LikeRepository.class);
    private final PostRepository posts = mock(PostRepository.class);
    private final IdGenerator idGenerator = mock(IdGenerator.class);
    private final Clock clock = mock(Clock.class);

    private final InteractionService service = new InteractionService(
            mock(CommentRepository.class), likes, posts, idGenerator, clock, new NoOpDomainEventPublisher());

    private final UUID postId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    @Test
    void likeWorksWithNoOpPublisher() {
        Instant now = Instant.parse("2026-11-18T12:00:00Z");
        when(clock.now()).thenReturn(now);
        when(idGenerator.generate()).thenReturn(UUID.randomUUID());
        when(posts.findById(postId)).thenReturn(Optional.of(
                new Post(postId, UUID.randomUUID(), "content", null, now, now, 0, 0)));
        when(likes.findByPostIdAndUserId(postId, userId)).thenReturn(Optional.empty());
        when(likes.countByPostId(postId)).thenReturn(1L);

        InteractionService.ToggleLikeResult result = service.toggleLike(postId, userId);

        assertThat(result).isEqualTo(new InteractionService.ToggleLikeResult(true, 1L));
        verify(likes).save(any(Like.class));
    }

    @Test
    void noOpPublisherAcceptsAnyEventWithoutFailing() {
        NoOpDomainEventPublisher publisher = new NoOpDomainEventPublisher();

        assertThatCode(() -> {
            publisher.publish(new Object());
            publisher.publish(null);
        }).doesNotThrowAnyException();
    }
}
