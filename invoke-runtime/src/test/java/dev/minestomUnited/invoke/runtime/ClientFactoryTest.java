package dev.minestomUnited.invoke.runtime;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientFactoryTest {
    static class CapturingHttp extends InvokeHttp {
        String lastUrl;
        String lastBody;
        Response next;

        CapturingHttp() {
            super(HttpClient.newHttpClient(), Duration.ofSeconds(1));
        }

        @Override
        public Response post(String url, String jsonBody) {
            lastUrl = url;
            lastBody = jsonBody;
            return next;
        }
    }

    static class ThrowingHttp extends InvokeHttp {
        final RuntimeException failure;

        ThrowingHttp(RuntimeException failure) {
            super(HttpClient.newHttpClient(), Duration.ofSeconds(1));
            this.failure = failure;
        }

        @Override
        public Response post(String url, String jsonBody) {
            throw failure;
        }
    }

    @Test
    void proxyPostsToServicePathAndDecodesResult() {
        CapturingHttp http = new CapturingHttp();
        http.next = new InvokeHttp.Response(200, "{\"result\":{\"status\":\"ok:p_80af\"}}");
        Fixtures.ReviewService client =
                InvokeClientFactory.create(Fixtures.ReviewService.class, "http://localhost:8080", http);
        Fixtures.ReviewResult out =
                client.resolveReview(new Fixtures.ReviewArgs("rv_abc", "p_80af"));
        assertEquals(new Fixtures.ReviewResult("ok:p_80af"), out);
        assertEquals("http://localhost:8080/ReviewService/resolveReview", http.lastUrl);
        assertEquals("{\"reviewId\":\"rv_abc\",\"personId\":\"p_80af\"}", http.lastBody);
    }

    @Test
    void proxyThrowsInvokeExceptionOnErrorEnvelope() {
        CapturingHttp http = new CapturingHttp();
        http.next = new InvokeHttp.Response(404,
                "{\"error\":{\"code\":404,\"message\":\"review not found\"}}");
        Fixtures.ReviewService client =
                InvokeClientFactory.create(Fixtures.ReviewService.class, "http://localhost:8080", http);
        InvokeException thrown = assertThrows(InvokeException.class,
                () -> client.resolveReview(new Fixtures.ReviewArgs("rv_missing", "p_80af")));
        assertEquals(404, thrown.code());
        assertEquals("review not found", thrown.getMessage());
    }

    @Test
    void proxySupportsVoidMethods() {
        CapturingHttp http = new CapturingHttp();
        http.next = new InvokeHttp.Response(200, "{\"result\":null}");
        Fixtures.ReviewService client =
                InvokeClientFactory.create(Fixtures.ReviewService.class, "http://localhost:8080/", http);
        client.pingReview(new Fixtures.ReviewArgs("rv_abc", "p_80af"));
        assertEquals("http://localhost:8080/ReviewService/pingReview", http.lastUrl);
    }

    @Test
    void proxyPropagatesTransportFailures() {
        Fixtures.ReviewService client = InvokeClientFactory.create(
                Fixtures.ReviewService.class, "http://localhost:8080",
                new ThrowingHttp(new InvokeException(503, "invoke transport failure: down")));
        InvokeException thrown = assertThrows(InvokeException.class,
                () -> client.resolveReview(new Fixtures.ReviewArgs("rv_abc", "p_80af")));
        assertEquals(503, thrown.code());
    }

    @Test
    void proxyRejectsNullResultForNonVoid() {
        CapturingHttp http = new CapturingHttp();
        http.next = new InvokeHttp.Response(200, "{\"result\":null}");
        Fixtures.ReviewService client =
                InvokeClientFactory.create(Fixtures.ReviewService.class, "http://localhost:8080", http);
        InvokeException thrown = assertThrows(InvokeException.class,
                () -> client.resolveReview(new Fixtures.ReviewArgs("rv_abc", "p_80af")));
        assertEquals(500, thrown.code());
    }

    @Test
    void httpTimeoutMapsTo504() {
        InvokeHttp http = new InvokeHttp(failingClient(new HttpTimeoutException("timed out")),
                Duration.ofMillis(50));
        InvokeException thrown = assertThrows(InvokeException.class,
                () -> http.post("http://localhost:8080/Svc/m", "{}"));
        assertEquals(504, thrown.code());
    }

    @Test
    void httpIoFailureMapsTo503() {
        InvokeHttp http = new InvokeHttp(failingClient(new IOException("connection refused")),
                Duration.ofSeconds(1));
        InvokeException thrown = assertThrows(InvokeException.class,
                () -> http.post("http://localhost:8080/Svc/m", "{}"));
        assertEquals(503, thrown.code());
        assertTrue(thrown.getMessage().contains("connection refused"));
    }

    private static HttpClient failingClient(IOException failure) {
        return new HttpClient() {
            @Override
            @SuppressWarnings("unchecked")
            public <T> HttpResponse<T> send(HttpRequest request,
                    HttpResponse.BodyHandler<T> responseBodyHandler) throws IOException {
                throw failure;
            }

            @Override
            public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request,
                    HttpResponse.BodyHandler<T> responseBodyHandler) {
                return CompletableFuture.failedFuture(failure);
            }

            @Override
            public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request,
                    HttpResponse.BodyHandler<T> responseBodyHandler,
                    HttpResponse.PushPromiseHandler<T> pushPromiseHandler) {
                return CompletableFuture.failedFuture(failure);
            }

            @Override
            public Optional<CookieHandler> cookieHandler() {
                return Optional.empty();
            }

            @Override
            public Optional<Duration> connectTimeout() {
                return Optional.empty();
            }

            @Override
            public Redirect followRedirects() {
                return Redirect.NEVER;
            }

            @Override
            public Optional<ProxySelector> proxy() {
                return Optional.empty();
            }

            @Override
            public SSLContext sslContext() {
                throw new UnsupportedOperationException();
            }

            @Override
            public SSLParameters sslParameters() {
                throw new UnsupportedOperationException();
            }

            @Override
            public Optional<Authenticator> authenticator() {
                return Optional.empty();
            }

            @Override
            public Version version() {
                return Version.HTTP_1_1;
            }

            @Override
            public Optional<Executor> executor() {
                return Optional.empty();
            }

            @Override
            public void close() {
            }
        };
    }
}
