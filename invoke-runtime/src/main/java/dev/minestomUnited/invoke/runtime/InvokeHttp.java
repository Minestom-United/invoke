package dev.minestomUnited.invoke.runtime;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.Objects;

/**
 * Blocking JDK HTTP transport for invoke clients. Every call is POST with a JSON
 * body, carries the timeout in X-Invoke-Timeout-Ms, and converts transport faults
 * into InvokeException: timeouts become 504, refused connections and interrupts
 * become 503. An interrupt re-sets the thread flag before throwing.
 */
public class InvokeHttp {
    /**
     * Raw HTTP outcome: status code plus body text. The body may be an envelope
     * or a proxy error page, so the client, not this class, decides its meaning.
     *
     * @param status the HTTP status code from the server
     * @param body the response body, never null
     */
    public record Response(int status, String body) {
        public Response {
            Objects.requireNonNull(body, "body");
        }
    }

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);

    private final HttpClient client;
    private final Duration timeout;

    /**
     * Creates a transport with a fresh JDK client and the 30-second default timeout.
     */
    public InvokeHttp() {
        this(HttpClient.newHttpClient(), DEFAULT_TIMEOUT);
    }

    /**
     * Creates a transport with an explicit client and timeout.
     *
     * @param client the JDK client used for every call
     * @param timeout per-call deadline, also sent as X-Invoke-Timeout-Ms
     */
    public InvokeHttp(HttpClient client, Duration timeout) {
        this.client = Objects.requireNonNull(client, "client");
        this.timeout = Objects.requireNonNull(timeout, "timeout");
    }

    /**
     * POSTs a JSON body and returns the raw response. Throws InvokeException(504)
     * on timeout and InvokeException(503) on connection failure or interrupt.
     *
     * @param url the full route URL including ServiceSimpleName/methodName
     * @param jsonBody the flat args object
     * @return status plus body, for the client to interpret
     * @throws InvokeException with code 504 or 503 on transport failure
     */
    public Response post(String url, String jsonBody) {
        Objects.requireNonNull(url, "url");
        Objects.requireNonNull(jsonBody, "jsonBody");
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(timeout)
                .header("Content-Type", "application/json")
                .header("X-Invoke-Timeout-Ms", Long.toString(timeout.toMillis()))
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();
        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            return new Response(response.statusCode(), response.body());
        } catch (HttpTimeoutException e) {
            throw new InvokeException(504, "invoke call timed out", e);
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new InvokeException(503, "invoke transport failure: " + e.getMessage(), e);
        }
    }
}
