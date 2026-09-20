package dev.minestomUnited.invoke.examples;

import dev.minestomUnited.invoke.runtime.InvokeClientFactory;
import dev.minestomUnited.invoke.runtime.InvokeEnvelopes;
import dev.minestomUnited.invoke.runtime.InvokeRegistry;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CollectionServiceTest {
    static CollectionService lobby() {
        InvokeRegistry registry = new InvokeRegistry();
        registry.register(new CollectionService.Example());
        LoopbackHttp http = new LoopbackHttp(registry);
        return InvokeClientFactory.create(CollectionService.class, "http://localhost:8080", http);
    }

    @Test
    void listsAndMapsRoundTripThroughClient() {
        CollectionService client = lobby();
        Map<String, Integer> counts = new LinkedHashMap<>();
        counts.put("a", 1);
        counts.put("b", 2);
        CollectionService.BagResult out = client.summarize(
                new CollectionService.BagArgs(List.of("a", "b"), List.of(0.5, 0.25), counts));

        assertEquals(new CollectionService.BagResult(4, 0.375), out);
    }

    @Test
    void listShapesEncodeToGoldenBytes() {
        Map<String, Integer> counts = new LinkedHashMap<>();
        counts.put("a", 1);
        String payload = InvokeEnvelopes.encodeRecord(CollectionService.BagArgs.CODEC,
                new CollectionService.BagArgs(List.of("a", "b"), List.of(0.5, 0.25), counts));
        assertEquals("{\"items\":[\"a\",\"b\"],\"scores\":[0.5,0.25],\"counts\":{\"a\":1}}", payload);
        assertEquals(payload, InvokeEnvelopes.encodeRecord(CollectionService.BagArgs.CODEC,
                InvokeEnvelopes.decodeRecord(CollectionService.BagArgs.CODEC, payload)));
    }
}
