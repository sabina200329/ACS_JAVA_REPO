package com.instakill.funnyfy;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FunnyfyIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private LocalLlmClient llm;

    private Cookie token;

    @BeforeEach
    void login() throws Exception {
        String name = "funny" + UUID.randomUUID().toString().substring(0, 8);
        token = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + name + "\",\"email\":\"" + name
                                + "@example.com\",\"password\":\"password\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getCookie("ACCESS_TOKEN");
    }

    @Test
    void returnsFunnierText() throws Exception {
        when(llm.complete(eq(FunnyfyService.buildMessages("I have a lab today")), anyDouble(), anyInt()))
                .thenReturn("\"I have a lab today, send snacks 🍕\"");

        mockMvc.perform(post("/api/funnyfy").cookie(token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"I have a lab today\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.text").value("I have a lab today, send snacks 🍕"));
    }

    @Test
    void llmNotRunningGives503WithMessage() throws Exception {
        when(llm.complete(anyList(), anyDouble(), anyInt()))
                .thenThrow(new LlmException(LlmException.Kind.UNAVAILABLE, "Serverul AI local nu răspunde", null));

        mockMvc.perform(post("/api/funnyfy").cookie(token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"hello\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.detail").value("Serverul AI local nu răspunde"));
    }

    @Test
    void llmTimeoutGives504() throws Exception {
        when(llm.complete(anyList(), anyDouble(), anyInt()))
                .thenThrow(new LlmException(LlmException.Kind.TIMEOUT, "timeout", null));

        mockMvc.perform(post("/api/funnyfy").cookie(token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"hello\"}"))
                .andExpect(status().isGatewayTimeout());
    }

    @Test
    void blankTextIsRejected() throws Exception {
        mockMvc.perform(post("/api/funnyfy").cookie(token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"   \"}"))
                .andExpect(status().isUnprocessableEntity()); // proiectul răspunde cu 422 la erori de validare
    }

    @Test
    void requiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/funnyfy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"hello\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createPostPageHasMakeItFunnierButton() throws Exception {
        mockMvc.perform(get("/posts/new").cookie(token))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Make It Funnier")));
    }
}
