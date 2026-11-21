package com.smartemail.writer.app;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.channel.ChannelOption;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientException;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Calls the Gemini generateContent REST endpoint and returns the generated reply text.
 * Business logic stays out of the controller so it can be tested in isolation.
 */
@Service
public class EmailGeneratorService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final WebClient webClient;
    private final String geminiApiUrl;
    private final String geminiApiKey;

    @Autowired
    public EmailGeneratorService(
            WebClient.Builder webClientBuilder,
            @Value("${gemini.api.url}") String geminiApiUrl,
            @Value("${gemini.api.key}") String geminiApiKey,
            @Value("${gemini.connect-timeout-ms:5000}") long connectTimeoutMs,
            @Value("${gemini.response-timeout-ms:30000}") long responseTimeoutMs) {
        this(builderWithTimeouts(webClientBuilder, connectTimeoutMs, responseTimeoutMs).build(),
                geminiApiUrl, geminiApiKey);
    }

    EmailGeneratorService(WebClient webClient, String geminiApiUrl, String geminiApiKey) {
        this.webClient = webClient;
        this.geminiApiUrl = geminiApiUrl;
        this.geminiApiKey = geminiApiKey;
    }

    public String generateEmailReply(EmailRequest emailRequest) {
        if (geminiApiKey == null || geminiApiKey.isBlank()) {
            throw new GeminiApiException("GEMINI_API_KEY is not configured. Set it before generating replies.");
        }

        Map<String, Object> requestBody = Map.of(
                "contents", List.of(Map.of("parts", List.of(Map.of("text", buildPrompt(emailRequest))))));

        String response;
        try {
            response = webClient.post()
                    .uri(geminiApiUrl + geminiApiKey)
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .bodyValue(requestBody)
                    .retrieve()
                    .onStatus(status -> status.isError(), clientResponse ->
                            clientResponse.bodyToMono(String.class)
                                    .defaultIfEmpty("")
                                    .map(body -> new GeminiApiException(
                                            "Gemini API returned " + clientResponse.statusCode().value()
                                                    + ": " + truncate(body),
                                            clientResponse.statusCode())))
                    .bodyToMono(String.class)
                    .block();
        } catch (GeminiApiException ex) {
            throw ex;
        } catch (WebClientException ex) {
            throw new GeminiApiException("Could not reach the Gemini API: " + ex.getMessage());
        }

        return extractResponseContent(response);
    }

    private String extractResponseContent(String response) {
        if (response == null || response.isBlank()) {
            throw new GeminiApiException("Gemini API returned an empty response.");
        }
        try {
            JsonNode root = MAPPER.readTree(response);
            JsonNode text = root.path("candidates").path(0).path("content").path("parts").path(0).path("text");
            if (!text.isTextual() || text.asText().isBlank()) {
                JsonNode error = root.path("error").path("message");
                String detail = error.isTextual() ? error.asText() : "Unexpected response shape.";
                throw new GeminiApiException("No reply text in Gemini response. " + detail);
            }
            return text.asText();
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            throw new GeminiApiException("Gemini API returned a non-JSON response.");
        }
    }

    private String buildPrompt(EmailRequest emailRequest) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Write a professional email reply to the message below. ")
                .append("Do not include a subject line. Return only the reply body.");
        if (emailRequest.getTone() != null && !emailRequest.getTone().isBlank()) {
            prompt.append(" Use a ").append(emailRequest.getTone().trim()).append(" tone.");
        }
        prompt.append("\n\nOriginal email:\n").append(emailRequest.getEmailContent().trim());
        return prompt.toString();
    }

    private static String truncate(String value) {
        return value.length() <= 300 ? value : value.substring(0, 300) + "...";
    }

    private static WebClient.Builder builderWithTimeouts(WebClient.Builder builder,
                                                         long connectTimeoutMs,
                                                         long responseTimeoutMs) {
        HttpClient httpClient = HttpClient.create()
                .responseTimeout(Duration.ofMillis(responseTimeoutMs))
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, (int) connectTimeoutMs);
        return builder.clone().clientConnector(new ReactorClientHttpConnector(httpClient));
    }
}
