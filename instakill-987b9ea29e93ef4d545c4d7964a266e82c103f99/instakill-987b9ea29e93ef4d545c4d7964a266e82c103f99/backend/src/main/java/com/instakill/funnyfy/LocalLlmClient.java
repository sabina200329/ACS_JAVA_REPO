package com.instakill.funnyfy;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.List;

/**
 * Client pentru un LLM local care expune un endpoint compatibil OpenAI
 * (POST /v1/chat/completions), de exemplu LM Studio.
 *
 * Adresa nu e scrisă în cod: vine din variabila de mediu LOCAL_LLM_URL
 * (vezi instakill.llm.* în application.yaml).
 */
@Component
public class LocalLlmClient {

    private final RestClient restClient;
    private final String url;
    private final String model;

    @Autowired
    public LocalLlmClient(RestClient.Builder builder,
                          @Value("${instakill.llm.url}") String url,
                          @Value("${instakill.llm.model}") String model,
                          @Value("${instakill.llm.connect-timeout:3s}") Duration connectTimeout,
                          @Value("${instakill.llm.read-timeout:30s}") Duration readTimeout) {
        this(builder.requestFactory(timeouts(connectTimeout, readTimeout)).build(), url, model);
    }

    /** Folosit în teste, cu un RestClient legat de un server simulat. */
    LocalLlmClient(RestClient restClient, String url, String model) {
        this.restClient = restClient;
        this.url = url;
        this.model = model;
    }

    private static SimpleClientHttpRequestFactory timeouts(Duration connectTimeout, Duration readTimeout) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) connectTimeout.toMillis());
        factory.setReadTimeout((int) readTimeout.toMillis());
        return factory;
    }

    /** Trimite un mesaj de sistem + mesajul utilizatorului și întoarce răspunsul modelului. */
    public String complete(String systemPrompt, String userMessage, double temperature, int maxTokens) {
        return complete(List.of(new Message("system", systemPrompt), new Message("user", userMessage)),
                temperature, maxTokens);
    }

    /** Trimite o conversație întreagă (system / user / assistant) și întoarce răspunsul modelului. */
    public String complete(List<Message> messages, double temperature, int maxTokens) {
        ChatRequest request = new ChatRequest(model, messages, temperature, maxTokens, false);
        ChatResponse response;
        try {
            response = restClient.post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(ChatResponse.class);
        } catch (ResourceAccessException e) {
            if (e.getCause() instanceof SocketTimeoutException
                    && e.getCause().getMessage() != null
                    && e.getCause().getMessage().toLowerCase().contains("read")) {
                throw new LlmException(LlmException.Kind.TIMEOUT, "The local AI model did not answer in time", e);
            }
            throw new LlmException(LlmException.Kind.UNAVAILABLE,
                    "The local AI server is not running. Start LM Studio on " + url, e);
        } catch (RestClientResponseException e) {
            throw new LlmException(LlmException.Kind.BAD_RESPONSE,
                    "The local AI server returned error " + e.getStatusCode().value(), e);
        } catch (RestClientException e) {
            throw new LlmException(LlmException.Kind.BAD_RESPONSE, "Invalid response from the local AI server", e);
        }

        if (response == null || response.choices() == null || response.choices().isEmpty()
                || response.choices().getFirst().message() == null
                || response.choices().getFirst().message().content() == null) {
            throw new LlmException(LlmException.Kind.BAD_RESPONSE, "The local AI server sent an empty answer", null);
        }
        return response.choices().getFirst().message().content();
    }

    // Formatul OpenAI chat completions (doar câmpurile de care avem nevoie)

    public record Message(String role, String content) {
    }

    record ChatRequest(String model,
                       List<Message> messages,
                       double temperature,
                       @JsonProperty("max_tokens") int maxTokens,
                       boolean stream) {
    }

    record Choice(Message message) {
    }

    record ChatResponse(List<Choice> choices) {
    }
}
