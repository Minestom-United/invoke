package dev.minestomUnited.invoke.examples;

import dev.minestomUnited.examples.generated.GeneratedServiceClient;
import dev.minestomUnited.invoke.runtime.InvokeRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GeneratedServiceTest {
    @Test
    void generatedClientCallsAnnotatedService() {
        InvokeRegistry registry = new InvokeRegistry();
        registry.register(new GeneratedService.Example());
        LoopbackHttp http = new LoopbackHttp(registry);

        GeneratedServiceClient client = new GeneratedServiceClient("http://localhost:8080", http);

        assertEquals(new GeneratedService.Greeting("hello, Ada"),
                client.greet(new GeneratedService.GreetArgs("Ada")));
        assertEquals("/GeneratedService/greet", http.lastUrl.substring(http.lastUrl.indexOf('/', 7)));
        assertEquals("{\"name\":\"Ada\"}", http.lastBody);

        client.forget(new GeneratedService.GreetArgs("Ada"));
    }
}
