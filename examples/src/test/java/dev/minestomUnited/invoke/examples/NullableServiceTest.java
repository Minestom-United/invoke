package dev.minestomUnited.invoke.examples;

import dev.minestomUnited.invoke.runtime.InvokeClientFactory;
import dev.minestomUnited.invoke.runtime.InvokeEnvelopes;
import dev.minestomUnited.invoke.runtime.InvokeRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class NullableServiceTest {
    static NullableService lobby() {
        InvokeRegistry registry = new InvokeRegistry();
        registry.register(new NullableService.Example());
        LoopbackHttp http = new LoopbackHttp(registry);
        return InvokeClientFactory.create(NullableService.class, "http://localhost:8080", http);
    }

    @Test
    void nullEncodesToDroppedKey() {
        assertEquals("{\"id\":\"x\"}",
                InvokeEnvelopes.encodeRecord(
                        NullableService.NoteArgs.CODEC, new NullableService.NoteArgs("x", null)));
    }

    @Test
    void absentKeyDecodesToNull() {
        NullableService.NoteArgs decoded =
                InvokeEnvelopes.decodeRecord(NullableService.NoteArgs.CODEC, "{\"id\":\"x\"}");
        assertEquals(new NullableService.NoteArgs("x", null), decoded);
    }

    @Test
    void explicitNullDecodesLikeAbsentKey() {
        NullableService.NoteArgs decoded = InvokeEnvelopes.decodeRecord(
                NullableService.NoteArgs.CODEC, "{\"id\":\"x\",\"maybe\":null}");
        assertEquals(new NullableService.NoteArgs("x", null), decoded);
    }

    @Test
    void nullEchoesEndToEndThroughClientAndRegistry() {
        NullableService.NoteResult out = lobby().save(new NullableService.NoteArgs("x", null));
        assertEquals("x", out.id());
        assertNull(out.maybeEcho());
    }

    @Test
    void presentValueEchoesEndToEnd() {
        NullableService.NoteResult out = lobby().save(new NullableService.NoteArgs("x", "hi"));
        assertEquals(new NullableService.NoteResult("x", "hi"), out);
    }

    @Test
    void dispatchOfExplicitNullIs200() {
        InvokeRegistry registry = new InvokeRegistry();
        registry.register(new NullableService.Example());
        InvokeRegistry.DispatchResult out =
                registry.dispatch("NullableService", "save", "{\"id\":\"x\",\"maybe\":null}");
        assertEquals(200, out.status());
        assertEquals("{\"result\":{\"id\":\"x\"}}", out.body());
    }
}
