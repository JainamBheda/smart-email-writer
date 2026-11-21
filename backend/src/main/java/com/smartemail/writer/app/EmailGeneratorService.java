package com.smartemail.writer.app;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.channel.ChannelOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientException;
import reactor.core.Exceptions;
import reactor.netty.http.client.HttpClient;
import reactor.util.retry.Retry;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeoutException;

/**
 * Calls the Gemini generateContent REST endpoint and returns the generated reply text.
 * v2 adds bounded retries for transient failures and a short-lived cache for identical prompts.
 */
@Service
public class EmailGeneratorService {

    private static final Logger log = LoggerFactory.getLogger(EmailGeneratorService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final Map<String, String> TONE_INSTRUCTIONS = Map.of(
            "professional", "Use a professional tone: formal, polite and neutral business language. Aim for 60-120 words.",
            "friendly", "Use a friendly tone: warm, conversational and positive. Aim for 60-120 words.",
            "assertive", "Use an assertive tone: direct and decisive, state your position and next steps clearly, avoid hedging. Aim for 40-100 words.",
            "concise", "Use a concise tone: answer only what is needed in 45 words or fewer. Omit greeting and sign-off.");

    private final WebClient webClient;
    private final String geminiApiUrl;
    private final String geminiApiKey;
    private final EmailReplyCache cache;
    private final Retry retrySpec;

    @Autowired
    public EmailGeneratorService(
            WebClient.Builder webClientBuilder,
            @Value("${gemini.api.url}") String geminiApiUrl,
            @Value("${gemini.api.key}") String geminiApiKey,
            @Value("${gemini.connect-timeout-ms:5000}") long connectTimeoutMs,
            @Value("${gemini.response-timeout-ms:60000}") long responseTimeoutMs,
            @Value("${gemini.max-retries:2}") int maxRetries,
            @Value("${gemini.retry-base-delay-ms:500}") long retryBaseDelayMs,
            @Value("${gemini.retry-max-delay-ms:8000}") long retryMaxDelayMs,
            EmailReplyCache cache) {
        this(builderWithTimeouts(webClientBuilder, connectTimeoutMs, responseTimeoutMs).build(),
                geminiApiUrl, geminiApiKey, cache,
                buildRetry(maxRetries, retryBaseDelayMs, retryMaxDelayMs));
    }

    EmailGeneratorService(WebClient webClient, String geminiApiUrl, String geminiApiKey) {
        this(webClient, geminiApiUrl, geminiApiKey, new EmailReplyCache(), Retry.max(0));
    }

    EmailGeneratorService(WebClient webClient, String geminiApiUrl, String geminiApiKey,
                          EmailReplyCache cache, Retry retrySpec) {
        this.webClient = webClient;
        this.geminiApiUrl = geminiApiUrl;
        this.geminiApiKey = geminiApiKey;
        this.cache = cache;
        this.retrySpec = retrySpec;
    }

    public record ReplyResult(String text, boolean fromCache) {
    }

    public String generateEmailReply(EmailRequest emailRequest) {
        return generate(emailRequest).text();
    }

    public ReplyResult generate(EmailRequest emailRequest) {
        if (geminiApiKey == null || geminiApiKey.isBlank()) {
            throw new GeminiApiException("GEMINI_API_KEY is not configured. Set it before generating replies.");
        }

        String key = cacheKey(emailRequest);
        String cached = cache.get(key);
        if (cached != null) {
            log.debug("Reply cache hit");
            return new ReplyResult(cached, true);
        }

        Map<String, Object> requestBody = buildRequestBody(emailRequest);
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
                    .retryWhen(retrySpec)
                    .block();
        } catch (RuntimeException ex) {
            throw translate(ex);
        }

        String reply = extractResponseContent(response);
        cache.put(key, reply);
        return new ReplyResult(reply, false);
    }

    /**
     * Reactor wraps the final failure in RetryExhaustedException once retries are used up.
     * Unwrap it so callers and the exception handler still see the meaningful error.
     */
    private static RuntimeException translate(Throwable failure) {
        Throwable cause = Exceptions.unwrap(failure);
        if (cause == failure && failure.getCause() != null
                && failure.getClass().getSimpleName().contains("RetryExhausted")) {
            cause = failure.getCause();
        }
        if (cause instanceof GeminiApiException geminiFailure) {
            if (geminiFailure.getStatusCode().value() == 429) {
                return new GeminiApiException(
                        "Gemini rate limit reached. Please wait a moment and try again.",
                        geminiFailure.getStatusCode());
            }
            return geminiFailure;
        }
        if (cause instanceof WebClientException webFailure) {
            return new GeminiApiException("Could not reach the Gemini API: " + webFailure.getMessage());
        }
        if (cause instanceof RuntimeException runtimeFailure) {
            return runtimeFailure;
        }
        return new GeminiApiException("Could not generate a reply: " + cause.getMessage());
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

    Map<String, Object> buildRequestBody(EmailRequest emailRequest) {
        return Map.of(
                "contents", List.of(Map.of("parts", List.of(Map.of("text", buildPrompt(emailRequest))))),
                "generationConfig", Map.of(
                        "temperature", 0.8,
                        "topP", 0.95,
                        "maxOutputTokens", 1024));
    }

    String buildPrompt(EmailRequest emailRequest) {
        String tone = emailRequest.getTone() == null ? "" : emailRequest.getTone().trim();
        StringBuilder prompt = new StringBuilder("Write an email reply to the message below. ");
        String instruction = TONE_INSTRUCTIONS.get(tone.toLowerCase(Locale.ROOT));
        if (tone.isEmpty()) {
            prompt.append("Use a professional tone. ");
        } else if (instruction != null) {
            prompt.append(instruction).append(" ");
        } else {
            prompt.append("Use a ").append(tone).append(" tone throughout the entire reply. ");
        }
        prompt.append("Do not include a subject line. Return only the reply body.");
        prompt.append("\n\nOriginal email:\n").append(emailRequest.getEmailContent().trim());
        return prompt.toString();
    }

    private static String cacheKey(EmailRequest emailRequest) {
        String tone = emailRequest.getTone() == null ? "" : emailRequest.getTone().trim().toLowerCase(Locale.ROOT);
        String raw = tone + "\u0000" + emailRequest.getEmailContent().trim();
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is required for reply caching", ex);
        }
    }

    private static Retry buildRetry(int maxRetries, long baseDelayMs, long maxDelayMs) {
        if (maxRetries <= 0) {
            return Retry.max(0);
        }
        long base = Math.max(1, baseDelayMs);
        long max = Math.max(base, maxDelayMs);
        return Retry.backoff(maxRetries, Duration.ofMillis(base))
                .maxBackoff(Duration.ofMillis(max))
                .jitter(0.3)
                .filter(EmailGeneratorService::isRetriable)
                .doBeforeRetry(signal -> log.warn("Retrying Gemini request after '{}' (retry {})",
                        signal.failure().getMessage(), signal.totalRetries() + 1));
    }

    private static boolean isRetriable(Throwable failure) {
        if (failure instanceof GeminiApiException ex) {
            int code = ex.getStatusCode().value();
            return code == 429 || code >= 500;
        }
        return failure instanceof WebClientException
                || failure instanceof IOException
                || failure instanceof TimeoutException;
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
