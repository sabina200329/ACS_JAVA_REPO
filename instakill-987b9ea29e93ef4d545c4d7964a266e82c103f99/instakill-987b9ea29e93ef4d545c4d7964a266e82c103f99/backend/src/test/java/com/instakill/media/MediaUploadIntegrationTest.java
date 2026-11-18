package com.instakill.media;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MediaUploadIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${media.base-path}")
    private String mediaBasePath;

    private Cookie token;

    @BeforeEach
    void registerUser() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String payload = "{\"username\":\"media" + suffix + "\",\"email\":\"media" + suffix
                + "@example.com\",\"password\":\"password\"}";
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andReturn();
        token = result.getResponse().getCookie("ACCESS_TOKEN");
    }

    @Test
    void uploadsVideoAndServesItUnderMediaPath() throws Exception {
        byte[] bytes = "fake mp4 bytes".getBytes();
        MockMultipartFile file = new MockMultipartFile("file", "clip.mp4", "video/mp4", bytes);

        MvcResult result = mockMvc.perform(multipart("/api/media").file(file).cookie(token))
                .andExpect(status().isCreated())
                .andReturn();

        String mediaUrl = mediaUrlOf(result);
        assertThat(mediaUrl).startsWith("/media/").endsWith(".mp4");

        Path stored = Path.of(mediaBasePath).resolve(mediaUrl.substring("/media/".length()));
        assertThat(Files.readAllBytes(stored)).isEqualTo(bytes);

        // fișierul se poate descărca din browser, fără autentificare
        MvcResult served = mockMvc.perform(get(mediaUrl))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(served.getResponse().getContentAsByteArray()).isEqualTo(bytes);
    }

    @Test
    void uploadsAndProcessesImage() throws Exception {
        byte[] png = ImageProcessingServiceTest.png(800, 400).readAllBytes();
        MockMultipartFile file = new MockMultipartFile("file", "photo.png", "image/png", png);

        MvcResult result = mockMvc.perform(multipart("/api/media").file(file).cookie(token))
                .andExpect(status().isCreated())
                .andReturn();

        Path stored = Path.of(mediaBasePath).resolve(mediaUrlOf(result).substring("/media/".length()));
        assertThat(javax.imageio.ImageIO.read(stored.toFile()).getWidth()).isEqualTo(300);
    }

    @Test
    void rejectsEmptyFile() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "empty.mp4", "video/mp4", new byte[0]);

        mockMvc.perform(multipart("/api/media").file(file).cookie(token))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsDisallowedExtension() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "script.exe", "application/octet-stream", "x".getBytes());

        mockMvc.perform(multipart("/api/media").file(file).cookie(token))
                .andExpect(status().isBadRequest());
    }

    @Test
    void requiresAuthentication() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "clip.mp4", "video/mp4", "x".getBytes());

        mockMvc.perform(multipart("/api/media").file(file))
                .andExpect(status().isUnauthorized());
    }

    private String mediaUrlOf(MvcResult result) throws Exception {
        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        return json.get("mediaUrl").asText();
    }
}
