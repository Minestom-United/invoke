package dev.minestomUnited.invoke.examples;

import dev.minestomUnited.invoke.runtime.InvokeHttp;
import dev.minestomUnited.invoke.runtime.InvokeRegistry;
import java.net.http.HttpClient;
import java.time.Duration;

final class LoopbackHttp extends InvokeHttp {
    private final InvokeRegistry registry;
    String lastUrl;
    String lastBody;

    LoopbackHttp(InvokeRegistry registry) {
        super(HttpClient.newHttpClient(), Duration.ofSeconds(5));
        this.registry = registry;
    }

    @Override
    public Response post(String url, String jsonBody) {
        lastUrl = url;
        lastBody = jsonBody;
        int slash = url.lastIndexOf('/');
        int prev = url.lastIndexOf('/', slash - 1);
        String service = url.substring(prev + 1, slash);
        String method = url.substring(slash + 1);
        InvokeRegistry.DispatchResult out = registry.dispatch(service, method, jsonBody);
        return new Response(out.status(), out.body());
    }
}
