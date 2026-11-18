package com.instakill.notification;

import com.instakill.post.domain.Post;
import com.instakill.user.domain.User;
import com.instakill.user.domain.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Trimite o notificare HTTP către ntfy.sh când se creează o postare.
 * Rulează asincron (@Async), deci crearea postării nu așteaptă după rețea.
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    private static final DateTimeFormatter TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    private final UserRepository users;
    private final RestClient restClient;
    private final String topicUrl;
    private final boolean enabled;

    public NotificationService(UserRepository users,
                               RestClient.Builder restClientBuilder,
                               @Value("${instakill.notifications.topic-url:https://ntfy.sh/instakill_sabina_demo}") String topicUrl,
                               @Value("${instakill.notifications.enabled:true}") boolean enabled) {
        this.users = users;
        this.restClient = restClientBuilder.build();
        this.topicUrl = topicUrl;
        this.enabled = enabled;
    }

    @Async
    public void sendPostCreated(Post post) {
        if (!enabled) {
            return;
        }
        try {
            String username = users.findById(post.authorId())
                    .map(User::username)
                    .orElse("necunoscut");

            restClient.post()
                    .uri(topicUrl)
                    .header("Title", "Postare noua pe Instakill")
                    .header("Tags", "camera")
                    .contentType(MediaType.TEXT_PLAIN)
                    .body(formatMessage(post, username))
                    .retrieve()
                    .toBodilessEntity();

            log.info("Notificare trimisă pentru postarea {}", post.id());
        } catch (Exception e) {
            // O notificare eșuată nu trebuie să afecteze crearea postării.
            log.warn("Notificarea pentru postarea {} a eșuat: {}", post.id(), e.getMessage());
        }
    }

    public String formatMessage(Post post, String username) {
        return String.format("Postare nouă de la @%s%nPost ID: %s%nData: %s",
                username, post.id(), TIMESTAMP.format(post.createdAt()));
    }
}
