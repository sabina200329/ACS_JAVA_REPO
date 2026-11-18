package com.instakill.notification;

import com.instakill.post.domain.Post;
import com.instakill.user.domain.User;
import com.instakill.user.domain.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class NotificationServiceTest {

    private static final String TOPIC = "https://ntfy.sh/test-topic";

    private final UserRepository users = mock(UserRepository.class);
    private final Post post = new Post(UUID.randomUUID(), UUID.randomUUID(), "hello", null,
            Instant.parse("2026-11-18T10:00:00Z"), Instant.parse("2026-11-18T10:00:00Z"), 0, 0);

    @Test
    void sendsFormattedMessageWithUsernameAndPostId() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        User author = new User(post.authorId(), "alice", "alice@example.com", "hash", Instant.now());
        when(users.findById(post.authorId())).thenReturn(Optional.of(author));

        server.expect(requestTo(TOPIC))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Title", "Postare noua pe Instakill"))
                .andExpect(content().string(org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.containsString("@alice"),
                        org.hamcrest.Matchers.containsString(post.id().toString()))))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        new NotificationService(users, builder, TOPIC, true).sendPostCreated(post);

        server.verify();
    }

    @Test
    void doesNothingWhenDisabled() {
        new NotificationService(users, RestClient.builder(), TOPIC, false).sendPostCreated(post);

        verifyNoInteractions(users);
    }

    @Test
    void swallowsErrorsSoPostCreationIsNotAffected() {
        when(users.findById(post.authorId())).thenThrow(new RuntimeException("db down"));

        new NotificationService(users, RestClient.builder(), TOPIC, true).sendPostCreated(post);
        // fără excepție = OK
    }

    @Test
    void messageContainsUsernamePostIdAndTimestamp() {
        String message = new NotificationService(users, RestClient.builder(), TOPIC, true)
                .formatMessage(post, "bob");

        assertThat(message)
                .contains("@bob")
                .contains(post.id().toString())
                .containsPattern("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}");
    }
}
