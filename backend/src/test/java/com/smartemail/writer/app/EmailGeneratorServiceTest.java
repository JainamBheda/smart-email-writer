package com.smartemail.writer.app;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientException;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import reactor.util.retry.Retry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmailGeneratorServiceTest {

    private static final String URL = "http://gemini.test/generate?key=";
    private static final String KEY = "test-key";

    @Test
    void extractsReplyTextFromGeminiResponse() {
        EmailGeneratorService service = serviceResponding(HttpStatus.OK,
                "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"Here is your reply.\"}]}}]}");

        String reply = service.generateEmailReply(new EmailRequest("Original message", "professional"));

        assertEquals("Here is your reply.", reply);
    }

    @Test
    void appendsApiKeyToConfiguredUrl() {
        AtomicReference<URI> calledUrl = new AtomicReference<>();
        WebClient webClient = WebClient.builder()
                .exchangeFunction(request -> {
                    calledUrl.set(request.url());
                    return Mono.just(jsonResponse(HttpStatus.OK,
                            "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"ok\"}]}}]}"));
                })
                .build();
        EmailGeneratorService service = new EmailGeneratorService(webClient, URL, KEY);

        service.generateEmailReply(new EmailRequest("Hello", "casual"));

        assertEquals(URI.create(URL + KEY), calledUrl.get());
    }

    @Test
    void toneIsAppliedWithoutConflictingProfessionalInstruction() {
        EmailGeneratorService service = new EmailGeneratorService(
                WebClient.builder().build(), URL, KEY);

        String casual = service.buildPrompt(new EmailRequest("Same email", "casual"));
        String professional = service.buildPrompt(new EmailRequest("Same email", "professional"));
        String defaultTone = service.buildPrompt(new EmailRequest("Same email", null));

        assertTrue(casual.contains("Use a casual tone throughout the entire reply."));
        assertFalse(casual.contains("professional"));
        assertTrue(professional.contains("Use a professional tone: formal, polite and neutral"));
        assertTrue(defaultTone.contains("Use a professional tone."));
        assertNotEquals(casual, professional);
    }

    @SuppressWarnings("unchecked")
    @Test
    void requestEnablesVariedGeneration() {
        EmailGeneratorService service = new EmailGeneratorService(
                WebClient.builder().build(), URL, KEY);

        Map<String, Object> body = service.buildRequestBody(new EmailRequest("Hello", "friendly"));
        Map<String, Object> config = (Map<String, Object>) body.get("generationConfig");

        assertTrue(config.containsKey("temperature"));
        assertTrue(((Number) config.get("temperature")).doubleValue() > 0);
    }

    @Test
    void failsClearlyWhenApiKeyIsMissing() {
        EmailGeneratorService service = new EmailGeneratorService(
                WebClient.builder().build(), URL, "");

        GeminiApiException ex = assertThrows(GeminiApiException.class,
                () -> service.generateEmailReply(new EmailRequest("Hello", null)));

        assertTrue(ex.getMessage().contains("GEMINI_API_KEY"));
    }

    @Test
    void failsClearlyOnMalformedResponse() {
        EmailGeneratorService service = serviceResponding(HttpStatus.OK, "not-json");

        GeminiApiException ex = assertThrows(GeminiApiException.class,
                () -> service.generateEmailReply(new EmailRequest("Hello", null)));

        assertTrue(ex.getMessage().contains("non-JSON"));
    }

    @Test
    void propagatesGeminiErrorStatus() {
        EmailGeneratorService service = serviceResponding(HttpStatus.TOO_MANY_REQUESTS,
                "{\"error\":{\"message\":\"Quota exceeded\"}}");

        GeminiApiException ex = assertThrows(GeminiApiException.class,
                () -> service.generateEmailReply(new EmailRequest("Hello", null)));

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, ex.getStatusCode());
        assertTrue(ex.getMessage().contains("rate limit"));
    }

    @Test
    void cachesIdenticalEmailAndTone() {
        AtomicInteger calls = new AtomicInteger();
        WebClient webClient = WebClient.builder()
                .exchangeFunction(request -> {
                    calls.incrementAndGet();
                    return Mono.just(jsonResponse(HttpStatus.OK,
                            "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"cached reply\"}]}}]}"));
                })
                .build();
        EmailGeneratorService service = new EmailGeneratorService(
                webClient, URL, KEY, new EmailReplyCache(), Retry.max(0));

        EmailRequest request = new EmailRequest("Hello", "friendly");
        EmailGeneratorService.ReplyResult first = service.generate(request);
        EmailGeneratorService.ReplyResult second = service.generate(request);
        service.generate(new EmailRequest("Hello", "professional"));

        assertFalse(first.fromCache());
        assertTrue(second.fromCache());
        assertEquals(2, calls.get());
    }

    @Test
    void retriesTransientGeminiErrors() {
        AtomicInteger calls = new AtomicInteger();
        WebClient webClient = WebClient.builder()
                .exchangeFunction(request -> {
                    if (calls.incrementAndGet() == 1) {
                        return Mono.just(jsonResponse(HttpStatus.TOO_MANY_REQUESTS,
                                "{\"error\":{\"message\":\"busy\"}}"));
                    }
                    return Mono.just(jsonResponse(HttpStatus.OK,
                            "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"ok\"}]}}]}"));
                })
                .build();
        Retry retry = Retry.backoff(1, Duration.ofMillis(1))
                .filter(failure -> failure instanceof GeminiApiException
                        || failure instanceof WebClientException);
        EmailGeneratorService service = new EmailGeneratorService(
                webClient, URL, KEY, new EmailReplyCache(), retry);

        assertEquals("ok", service.generateEmailReply(new EmailRequest("Hello", "friendly")));
        assertEquals(2, calls.get());
    }

    private static EmailGeneratorService serviceResponding(HttpStatus status, String body) {
        return new EmailGeneratorService(
                WebClient.builder().exchangeFunction(request -> Mono.just(jsonResponse(status, body))).build(),
                URL, KEY);
    }

    private static ClientResponse jsonResponse(HttpStatus status, String body) {
        return ClientResponse.create(status)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .body(body)
                .build();
    }
}
