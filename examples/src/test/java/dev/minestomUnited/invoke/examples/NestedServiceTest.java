package dev.minestomUnited.invoke.examples;

import dev.minestomUnited.invoke.runtime.InvokeClientFactory;
import dev.minestomUnited.invoke.runtime.InvokeEnvelopes;
import dev.minestomUnited.invoke.runtime.InvokeRegistry;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NestedServiceTest {
    @Test
    void nestedRecordRoundTripsThroughClientAndRegistry() {
        InvokeRegistry registry = new InvokeRegistry();
        registry.register(new NestedService.Example());
        LoopbackHttp http = new LoopbackHttp(registry);
        NestedService client = InvokeClientFactory.create(NestedService.class, "http://localhost:8080", http);

        NestedService.CallArgs args = new NestedService.CallArgs(
                new NestedService.Turn("SPEAKER_01", 1.5, 2.5), List.of("x"));
        NestedService.CallResult out = client.describe(args);

        assertEquals("http://localhost:8080/NestedService/describe", http.lastUrl);
        assertEquals(
                "{\"turn\":{\"speaker\":\"SPEAKER_01\",\"start\":1.5,\"end\":2.5},\"tags\":[\"x\"]}",
                http.lastBody);
        assertEquals(new NestedService.CallResult("SPEAKER_01 1.5-2.5 x"), out);
    }

    @Test
    void nestedGoldenVectorDecodesAndReencodesByteIdentically() {
        String vector = "{\"turn\":{\"speaker\":\"SPEAKER_01\",\"start\":1.5,\"end\":2.5},\"tags\":[\"x\"]}";
        NestedService.CallArgs decoded =
                InvokeEnvelopes.decodeRecord(NestedService.CallArgs.CODEC, vector);
        assertEquals(vector, InvokeEnvelopes.encodeRecord(NestedService.CallArgs.CODEC, decoded));
    }
}
