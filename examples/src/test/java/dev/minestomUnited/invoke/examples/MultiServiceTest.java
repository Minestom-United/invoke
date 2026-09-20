package dev.minestomUnited.invoke.examples;

import dev.minestomUnited.invoke.runtime.InvokeClientFactory;
import dev.minestomUnited.invoke.runtime.InvokeRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MultiServiceTest {
    @Test
    void threeMethodsShareOneServiceAndOneClient() {
        InvokeRegistry registry = new InvokeRegistry();
        registry.register(new MultiService.Example());
        LoopbackHttp http = new LoopbackHttp(registry);
        MultiService client =
                InvokeClientFactory.create(MultiService.class, "http://localhost:8080", http);

        assertFalse(client.put(new MultiService.PutArgs("k", "v")).replaced());
        assertEquals(new MultiService.GetResult("v"), client.get(new MultiService.GetArgs("k")));
        assertTrue(client.put(new MultiService.PutArgs("k", "v2")).replaced());
        client.clear(new MultiService.ClearArgs("k"));
        assertEquals("http://localhost:8080/MultiService/clear", http.lastUrl);
        assertEquals(new MultiService.GetResult(""), client.get(new MultiService.GetArgs("k")));
    }
}
