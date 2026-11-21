package com.smartemail.writer.app;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
        assertTrue(ex.getMessage().contains("Quota exceeded"));
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
