package com.instakill.analytics;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AnalyticsIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void computesStatisticsForCurrentUser() throws Exception {
        Cookie author = register("author");
        Cookie fan1 = register("fan");
        Cookie fan2 = register("fan");

        String photoPost = createPost(author, "photo post", "/media/a.jpg");
        String videoPost = createPost(author, "video post", "/media/b.mp4");
        createPost(author, "text only", null);

        // photo: 2 like-uri + 1 comentariu = 3; video: 1 like + 0 comentarii = 1; text: 0
        like(fan1, photoPost);
        like(fan2, photoPost);
        like(fan1, videoPost);
        comment(fan1, photoPost);

        JsonNode stats = analytics(author);

        // 1. pe săptămână
        assertThat(stats.get("weekly")).hasSize(1);
        JsonNode week = stats.get("weekly").get(0);
        assertThat(week.get("posts").asLong()).isEqualTo(3);
        assertThat(week.get("likes").asLong()).isEqualTo(3);
        assertThat(week.get("comments").asLong()).isEqualTo(1);
        LocalDate weekStart = LocalDate.parse(week.get("weekStart").asText());
        assertThat(weekStart.getDayOfWeek()).isEqualTo(java.time.DayOfWeek.MONDAY);

        // 2. top postări
        JsonNode top = stats.get("topPosts");
        assertThat(top).hasSize(3);
        assertThat(top.get(0).get("id").asText()).isEqualTo(photoPost);
        assertThat(top.get(0).get("engagement").asLong()).isEqualTo(3);
        assertThat(top.get(1).get("id").asText()).isEqualTo(videoPost);

        // 3. raport like/comentariu
        assertThat(stats.get("likeCommentRatio").get("likes").asLong()).isEqualTo(3);
        assertThat(stats.get("likeCommentRatio").get("comments").asLong()).isEqualTo(1);
        assertThat(stats.get("likeCommentRatio").get("ratio").asDouble()).isEqualTo(3.0);

        // 4. media like-uri pe tip
        JsonNode byType = stats.get("byMediaType");
        assertThat(averageLikes(byType, "photo")).isEqualTo(2.0);
        assertThat(averageLikes(byType, "video")).isEqualTo(1.0);
        assertThat(averageLikes(byType, "text")).isEqualTo(0.0);

        // 5. rang: autorul are engagement mediu 4/3 > 0, deci e pe locul 1
        JsonNode rank = stats.get("engagementRank");
        assertThat(rank.get("rank").asLong()).isEqualTo(1);
        assertThat(rank.get("averageEngagement").asDouble()).isEqualTo(1.33);
        assertThat(rank.get("topPercent").asDouble()).isLessThanOrEqualTo(100.0);
    }

    @Test
    void userWithoutPostsGetsEmptyStatistics() throws Exception {
        JsonNode stats = analytics(register("empty"));

        assertThat(stats.get("weekly")).isEmpty();
        assertThat(stats.get("topPosts")).isEmpty();
        assertThat(stats.get("byMediaType")).isEmpty();
        assertThat(stats.get("likeCommentRatio").get("ratio").isNull()).isTrue();
        assertThat(stats.get("engagementRank").isNull()).isTrue();
    }

    @Test
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/analytics/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void rendersAnalyticsPage() throws Exception {
        Cookie user = register("page");
        createPost(user, "hello", null);

        mockMvc.perform(get("/analytics").cookie(user))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("My analytics")));
    }

    private double averageLikes(JsonNode byType, String type) {
        for (JsonNode row : byType) {
            if (row.get("mediaType").asText().equals(type)) {
                return row.get("averageLikes").asDouble();
            }
        }
        throw new AssertionError("missing media type " + type);
    }

    private Cookie register(String prefix) throws Exception {
        String name = prefix + UUID.randomUUID().toString().substring(0, 8);
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + name + "\",\"email\":\"" + name
                                + "@example.com\",\"password\":\"password\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return result.getResponse().getCookie("ACCESS_TOKEN");
    }

    private String createPost(Cookie user, String content, String imageUrl) throws Exception {
        String body = objectMapper.writeValueAsString(java.util.Collections.unmodifiableMap(new java.util.HashMap<>() {{
            put("content", content);
            put("imageUrl", imageUrl);
        }}));
        MvcResult result = mockMvc.perform(post("/api/posts")
                        .cookie(user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    private void like(Cookie user, String postId) throws Exception {
        mockMvc.perform(post("/api/posts/" + postId + "/likes/toggle").cookie(user))
                .andExpect(status().isOk());
    }

    private void comment(Cookie user, String postId) throws Exception {
        mockMvc.perform(post("/api/posts/" + postId + "/comments")
                        .cookie(user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"nice\"}"))
                .andExpect(status().isCreated());
    }

    private JsonNode analytics(Cookie user) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/analytics/me").cookie(user))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }
}
