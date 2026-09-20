package dev.minestomUnited.invoke.examples;

import dev.minestomUnited.invoke.runtime.InvokeClientFactory;
import dev.minestomUnited.invoke.runtime.InvokeException;
import dev.minestomUnited.invoke.runtime.InvokeRegistry;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LookupServiceTest {
    static LookupService lobby() {
        InvokeRegistry registry = new InvokeRegistry();
        registry.register(new LookupService.Example(Map.of("a", "alpha")));
        LoopbackHttp http = new LoopbackHttp(registry);
        return InvokeClientFactory.create(LookupService.class, "http://localhost:8080", http);
    }

    @Test
    void hitReturnsItem() {
        assertEquals(new LookupService.ItemResult("a", "alpha"),
                lobby().lookup(new LookupService.KeyArgs("a")));
    }

    @Test
    void implThrowBecomes404ErrorEnvelope() {
        InvokeRegistry registry = new InvokeRegistry();
        registry.register(new LookupService.Example(Map.of()));
        InvokeRegistry.DispatchResult out =
                registry.dispatch("LookupService", "lookup", "{\"id\":\"missing\"}");
        assertEquals(404, out.status());
        assertEquals("{\"error\":{\"code\":404,\"message\":\"item not found: missing\"}}", out.body());
    }

    @Test
    void clientSeesTheSameCode() {
        InvokeException thrown = assertThrows(InvokeException.class,
                () -> lobby().lookup(new LookupService.KeyArgs("missing")));
        assertEquals(404, thrown.code());
        assertEquals("item not found: missing", thrown.getMessage());
    }
}
