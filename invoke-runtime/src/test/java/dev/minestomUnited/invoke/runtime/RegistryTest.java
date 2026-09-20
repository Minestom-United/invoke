package dev.minestomUnited.invoke.runtime;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegistryTest {
    static class Reviews implements Fixtures.ReviewService {
        @Override
        public Fixtures.ReviewResult resolveReview(Fixtures.ReviewArgs args) {
            if (args.reviewId().equals("rv_missing")) {
                throw new InvokeException(404, "review not found");
            }
            if (args.reviewId().equals("rv_boom")) {
                throw new IllegalStateException("db connection pool exhausted");
            }
            return new Fixtures.ReviewResult("ok:" + args.personId());
        }

        @Override
        public void pingReview(Fixtures.ReviewArgs args) {
        }
    }

    private static InvokeRegistry registry() {
        InvokeRegistry registry = new InvokeRegistry();
        registry.register(new Reviews());
        return registry;
    }

    @Test
    void successDispatchIs200WithResultEnvelope() {
        InvokeRegistry.DispatchResult out = registry().dispatch(
                "ReviewService", "resolveReview", "{\"reviewId\":\"rv_abc\",\"personId\":\"p_80af\"}");
        assertEquals(200, out.status());
        assertEquals("{\"result\":{\"status\":\"ok:p_80af\"}}", out.body());
    }

    @Test
    void voidDispatchIs200WithNullResult() {
        InvokeRegistry.DispatchResult out = registry().dispatch(
                "ReviewService", "pingReview", "{\"reviewId\":\"rv_abc\",\"personId\":\"p_80af\"}");
        assertEquals(200, out.status());
        assertEquals("{\"result\":null}", out.body());
    }

    @Test
    void invokeExceptionPassesCodeThrough() {
        InvokeRegistry.DispatchResult out = registry().dispatch(
                "ReviewService", "resolveReview", "{\"reviewId\":\"rv_missing\",\"personId\":\"p_80af\"}");
        assertEquals(404, out.status());
        assertEquals("{\"error\":{\"code\":404,\"message\":\"review not found\"}}", out.body());
    }

    @Test
    void unexpectedExceptionMapsTo500WithoutLeak() {
        InvokeRegistry.DispatchResult out = registry().dispatch(
                "ReviewService", "resolveReview", "{\"reviewId\":\"rv_boom\",\"personId\":\"p_80af\"}");
        assertEquals(500, out.status());
        assertEquals("{\"error\":{\"code\":500,\"message\":\"internal error\"}}", out.body());
        assertFalse(out.body().contains("db connection pool"));
    }

    @Test
    void unknownMethodMapsTo404() {
        InvokeRegistry.DispatchResult out =
                registry().dispatch("ReviewService", "nope", "{}");
        assertEquals(404, out.status());
        InvokeEnvelopes.Envelope decoded = InvokeEnvelopes.decode(out.body());
        assertTrue(decoded instanceof InvokeEnvelopes.Envelope.Error error && error.code() == 404);
    }

    @Test
    void unknownServiceMapsTo404() {
        InvokeRegistry.DispatchResult out = registry().dispatch("NopeService", "go", "{}");
        assertEquals(404, out.status());
    }

    @Test
    void badArgsShapeMapsTo400() {
        String[] bad = {"{}", "{\"reviewId\":\"rv_abc\"}", "{\"reviewId\":[\"rv_abc\"],\"personId\":\"p_80af\"}",
                "{\"reviewId\":{\"id\":\"rv_abc\"},\"personId\":\"p_80af\"}",
                "not json", "[1,2]", "null"};
        for (String body : bad) {
            InvokeRegistry.DispatchResult out =
                    registry().dispatch("ReviewService", "resolveReview", body);
            assertEquals(400, out.status(), "body: " + body);
            InvokeEnvelopes.Envelope decoded = InvokeEnvelopes.decode(out.body());
            assertTrue(decoded instanceof InvokeEnvelopes.Envelope.Error error && error.code() == 400,
                    "body: " + body);
        }
    }

    @Test
    void nullReturnForNonVoidMapsTo500() {
        InvokeRegistry registry = new InvokeRegistry();
        registry.register(new Fixtures.ReviewService() {
            @Override
            public Fixtures.ReviewResult resolveReview(Fixtures.ReviewArgs args) {
                return null;
            }

            @Override
            public void pingReview(Fixtures.ReviewArgs args) {
            }
        });
        InvokeRegistry.DispatchResult out = registry.dispatch(
                "ReviewService", "resolveReview", "{\"reviewId\":\"rv_abc\",\"personId\":\"p_80af\"}");
        assertEquals(500, out.status());
    }

    @Test
    void overloadedMethodsAreRejected() {
        InvokeRegistry registry = new InvokeRegistry();
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                () -> registry.register(new Fixtures.OverloadedService() {
                    @Override
                    public Fixtures.ReviewResult go(Fixtures.ReviewArgs args) {
                        return null;
                    }

                    @Override
                    public Fixtures.ReviewResult go(Fixtures.SampleArgs args) {
                        return null;
                    }
                }));
        assertTrue(thrown.getMessage().contains("overloaded"));
    }

    @Test
    void zeroArgMethodsAreRejected() {
        InvokeRegistry registry = new InvokeRegistry();
        assertThrows(IllegalArgumentException.class,
                () -> registry.register(new Fixtures.ZeroArgService() {
                    @Override
                    public Fixtures.ReviewResult go() {
                        return null;
                    }
                }));
    }

    @Test
    void twoArgMethodsAreRejected() {
        InvokeRegistry registry = new InvokeRegistry();
        assertThrows(IllegalArgumentException.class,
                () -> registry.register(new Fixtures.TwoArgService() {
                    @Override
                    public Fixtures.ReviewResult go(Fixtures.ReviewArgs a, Fixtures.ReviewArgs b) {
                        return null;
                    }
                }));
    }

    @Test
    void nonRecordArgsAreRejected() {
        InvokeRegistry registry = new InvokeRegistry();
        assertThrows(IllegalArgumentException.class,
                () -> registry.register(new Fixtures.NonRecordArgService() {
                    @Override
                    public Fixtures.ReviewResult go(String raw) {
                        return null;
                    }
                }));
    }

    @Test
    void nonRecordReturnsAreRejected() {
        InvokeRegistry registry = new InvokeRegistry();
        assertThrows(IllegalArgumentException.class,
                () -> registry.register(new Fixtures.NonRecordReturnService() {
                    @Override
                    public String go(Fixtures.ReviewArgs args) {
                        return null;
                    }
                }));
    }

    @Test
    void missingCodecIsRejected() {
        InvokeRegistry registry = new InvokeRegistry();
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                () -> registry.register(new Fixtures.MissingCodecService() {
                    @Override
                    public Fixtures.ReviewResult go(Fixtures.NoCodecArgs args) {
                        return null;
                    }
                }));
        assertTrue(thrown.getMessage().contains("CODEC"));
    }
}
