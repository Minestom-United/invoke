package dev.minestomUnited.invoke.examples;

import dev.minestomUnited.invoke.runtime.InvokeClientFactory;
import dev.minestomUnited.invoke.runtime.InvokeRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VoidServiceTest {
    @Test
    void voidCallPostsFlatArgsAndReturnsNullResult() {
        InvokeRegistry registry = new InvokeRegistry();
        VoidService.Example impl = new VoidService.Example();
        registry.register(impl);
        LoopbackHttp http = new LoopbackHttp(registry);
        VoidService client = InvokeClientFactory.create(VoidService.class, "http://localhost:8080", http);

        client.ping(new VoidService.PingArgs("p1"));

        assertTrue(impl.seen("p1"));
        assertEquals("http://localhost:8080/VoidService/ping", http.lastUrl);
        assertEquals("{\"id\":\"p1\"}", http.lastBody);
        InvokeRegistry.DispatchResult raw =
                registry.dispatch("VoidService", "ping", "{\"id\":\"p1\"}");
        assertEquals(200, raw.status());
        assertEquals("{\"result\":null}", raw.body());
    }
}
