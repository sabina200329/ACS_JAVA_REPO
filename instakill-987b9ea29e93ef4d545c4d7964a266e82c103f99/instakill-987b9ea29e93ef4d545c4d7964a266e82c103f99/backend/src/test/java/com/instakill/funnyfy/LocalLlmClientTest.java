package com.instakill.funnyfy;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class LocalLlmClientTest {

    private static final String URL = "http://llm.test/v1/chat/completions";

    @Test
    void sendsOpenAiChatRequestAndReturnsFirstChoice() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        LocalLlmClient client = new LocalLlmClient(builder.build(), URL, "google/gemma-3-270m");

        server.expect(requestTo(URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.model").value("google/gemma-3-270m"))
                .andExpect(jsonPath("$.messages[0].role").value("system"))
                .andExpect(jsonPath("$.messages[1].role").value("user"))
                .andExpect(jsonPath("$.messages[1].content").value("hello"))
                .andExpect(jsonPath("$.max_tokens").value(256))
                .andExpect(jsonPath("$.stream").value(false))
                .andRespond(withSuccess("""
                        {"id":"x","object":"chat.completion",
                         "choices":[{"index":0,"message":{"role":"assistant","content":"HELLO 😂"},"finish_reason":"stop"}],
                         "usage":{"total_tokens":5}}
                        """, MediaType.APPLICATION_JSON));

        assertThat(client.complete("be funny", "hello", 0.7, 256)).isEqualTo("HELLO 😂");
        server.verify();
    }

    @Test
    void serverErrorBecomesBadGateway() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        LocalLlmClient client = new LocalLlmClient(builder.build(), URL, "m");
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThatThrownBy(() -> client.complete("s", "u", 0.7, 10))
                .isInstanceOf(LlmException.class)
                .extracting(e -> ((LlmException) e).status())
                .isEqualTo(HttpStatus.BAD_GATEWAY);
    }

    @Test
    void emptyChoicesBecomeBadGateway() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        LocalLlmClient client = new LocalLlmClient(builder.build(), URL, "m");
        server.expect(requestTo(URL)).andRespond(withSuccess("{\"choices\":[]}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.complete("s", "u", 0.7, 10))
                .isInstanceOf(LlmException.class)
                .extracting(e -> ((LlmException) e).kind())
                .isEqualTo(LlmException.Kind.BAD_RESPONSE);
    }

    @Test
    void serverNotRunningBecomesServiceUnavailable() {
        // port 1 pe localhost: nimic nu ascultă acolo -> conexiune refuzată
        LocalLlmClient client = new LocalLlmClient(RestClient.builder(), "http://127.0.0.1:1/v1/chat/completions",
                "m", Duration.ofSeconds(1), Duration.ofSeconds(1));

        assertThatThrownBy(() -> client.complete("s", "u", 0.7, 10))
                .isInstanceOf(LlmException.class)
                .extracting(e -> ((LlmException) e).status())
                .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }
}
