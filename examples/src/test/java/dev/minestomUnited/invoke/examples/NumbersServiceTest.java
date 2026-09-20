package dev.minestomUnited.invoke.examples;

import dev.minestomUnited.invoke.runtime.InvokeClientFactory;
import dev.minestomUnited.invoke.runtime.InvokeEnvelopes;
import dev.minestomUnited.invoke.runtime.InvokeRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NumbersServiceTest {
    @Test
    void intLongDoubleBooleanRoundTripThroughClient() {
        InvokeRegistry registry = new InvokeRegistry();
        registry.register(new NumbersService.Example());
        LoopbackHttp http = new LoopbackHttp(registry);
        NumbersService client =
                InvokeClientFactory.create(NumbersService.class, "http://localhost:8080", http);

        NumbersService.MeasureResult out =
                client.measure(new NumbersService.MeasureArgs(4, 9_000_000_000L, 1.5, true));

        assertEquals("{\"count\":4,\"total\":9000000000,\"ratio\":1.5,\"ok\":true}", http.lastBody);
        assertEquals(new NumbersService.MeasureResult(9_000_000_006L, true), out);
    }

    @Test
    void falseAndZeroEncodeExactly() {
        String payload = InvokeEnvelopes.encodeRecord(NumbersService.MeasureArgs.CODEC,
                new NumbersService.MeasureArgs(0, 0L, 0.0, false));
        assertEquals("{\"count\":0,\"total\":0,\"ratio\":0.0,\"ok\":false}", payload);
        assertEquals(new NumbersService.MeasureArgs(0, 0L, 0.0, false),
                InvokeEnvelopes.decodeRecord(NumbersService.MeasureArgs.CODEC, payload));
    }
}
