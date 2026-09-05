package com.groww.smart_watchlist.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plain unit tests for {@link GlobalExceptionHandler} — deliberately NOT a
 * {@code @SpringBootTest}: the handler itself has no dependencies (no DB,
 * no other beans), so it can be instantiated and called directly like any
 * other plain object. Covers only the new generic-exception fallback added
 * alongside the specific mappings (those already have coverage implicit in
 * every other test that expects a 404/409/403); this test exists so a
 * genuinely unexpected failure is proven to degrade to a clean, generic 500
 * instead of leaking an internal exception message or stack trace to the
 * client.
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void unexpectedExceptionMapsToGeneric500WithoutLeakingInternalDetails() {
        RuntimeException internal = new RuntimeException("db connection pool exhausted at host 10.0.0.7:5432");

        ResponseEntity<Map<String, Object>> response = handler.handleUnexpected(internal);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertEquals(500, body.get("status"));
        assertEquals("Internal Server Error", body.get("error"));
        assertNotNull(body.get("timestamp"));

        String message = String.valueOf(body.get("message"));
        assertTrue(message.equals("An unexpected error occurred. Please try again."),
                "client-facing message must be the generic fallback text, not the real exception message");
        assertFalse(message.contains("10.0.0.7"), "client-facing message must never leak internal details");
        assertFalse(message.toLowerCase().contains("exception"),
                "client-facing message must not hint at internal implementation details");
    }

    @Test
    void unexpectedExceptionResponseNeverIncludesAStackTraceField() {
        ResponseEntity<Map<String, Object>> response =
                handler.handleUnexpected(new IllegalStateException("boom"));

        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        // The response body is a small, fixed set of fields (timestamp/status/error/message) —
        // assert exactly that shape rather than merely "no field called stackTrace",
        // so an unrelated future field slipping in would also fail this test.
        assertEquals(Set.of("timestamp", "status", "error", "message"), body.keySet());
    }
}
