package dev.minestomUnited.invoke.runtime;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EnvelopeTest {
    @Test
    void errorVectorIsByteExact() {
        assertEquals(
                "{\"error\":{\"code\":404,\"message\":\"review not found\"}}",
                InvokeEnvelopes.encodeError(404, "review not found"));
    }

    @Test
    void voidResultVectorIsByteExact() {
        assertEquals("{\"result\":null}", InvokeEnvelopes.encodeVoidResult());
    }

    @Test
    void errorVectorRoundTripIsIdentical() {
        String vector = "{\"error\":{\"code\":404,\"message\":\"review not found\"}}";
        InvokeEnvelopes.Envelope decoded = InvokeEnvelopes.decode(vector);
        assertTrue(decoded instanceof InvokeEnvelopes.Envelope.Error error
                && error.code() == 404
                && error.message().equals("review not found"));
        InvokeEnvelopes.Envelope.Error error = (InvokeEnvelopes.Envelope.Error) decoded;
        assertEquals(vector, InvokeEnvelopes.encodeError(error.code(), error.message()));
    }

    @Test
    void voidResultRoundTripIsIdentical() {
        String vector = "{\"result\":null}";
        InvokeEnvelopes.Envelope decoded = InvokeEnvelopes.decode(vector);
        assertTrue(decoded instanceof InvokeEnvelopes.Envelope.Result result
                && result.payloadJson().equals("null"));
        assertEquals(vector, InvokeEnvelopes.encodeResult(
                ((InvokeEnvelopes.Envelope.Result) decoded).payloadJson()));
    }

    @Test
    void resultPayloadRoundTripIsIdentical() {
        String payload = "{\"reviewId\":\"rv_abc\",\"personId\":\"p_80af\"}";
        String envelope = InvokeEnvelopes.encodeResult(payload);
        assertEquals("{\"result\":" + payload + "}", envelope);
        InvokeEnvelopes.Envelope decoded = InvokeEnvelopes.decode(envelope);
        assertTrue(decoded instanceof InvokeEnvelopes.Envelope.Result result
                && result.payloadJson().equals(payload));
        assertEquals(envelope, InvokeEnvelopes.encodeResult(
                ((InvokeEnvelopes.Envelope.Result) decoded).payloadJson()));
    }

    @Test
    void errorMessageEscapingRoundTrips() {
        String message = "quote:\" backslash:\\ newline:\n tab:\t snowman:\u2603";
        String encoded = InvokeEnvelopes.encodeError(400, message);
        InvokeEnvelopes.Envelope decoded = InvokeEnvelopes.decode(encoded);
        assertTrue(decoded instanceof InvokeEnvelopes.Envelope.Error error
                && error.code() == 400
                && error.message().equals(message));
        assertEquals(encoded, InvokeEnvelopes.encodeError(
                ((InvokeEnvelopes.Envelope.Error) decoded).code(),
                ((InvokeEnvelopes.Envelope.Error) decoded).message()));
    }

    @Test
    void codecBuiltErrorBodyMatchesEnvelopeBytes() {
        InvokeEnvelopes.ErrorBody body = new InvokeEnvelopes.ErrorBody(404, "review not found");
        String payload = InvokeEnvelopes.encodeRecord(InvokeEnvelopes.errorBodyCodec(), body);
        assertEquals("{\"code\":404,\"message\":\"review not found\"}", payload);
        assertEquals(
                "{\"error\":" + payload + "}",
                InvokeEnvelopes.encodeError(body.code(), body.message()));
    }

    @Test
    void malformedEnvelopesThrow500() {
        String[] bad = {"not json", "[1,2]", "42", "\"str\"", "{}", "{\"other\":1}",
                "{\"error\":{\"code\":\"x\"}}", "{\"error\":{}}", "{\"error\":[]}"};
        for (String body : bad) {
            InvokeException thrown = assertThrows(InvokeException.class,
                    () -> InvokeEnvelopes.decode(body), "body: " + body);
            assertEquals(500, thrown.code(), "body: " + body);
        }
    }

    @Test
    void goldenPayloadVectorsRoundTrip() {
        assertRoundTrip(Fixtures.ReviewArgs.CODEC,
                "{\"reviewId\":\"rv_abc\",\"personId\":\"p_80af\"}");
        assertRoundTrip(Fixtures.SampleArgs.CODEC,
                "{\"name\":\"\",\"samples\":0,\"confirmed\":false}");
        assertRoundTrip(Fixtures.CallArgs.CODEC,
                "{\"turn\":{\"speaker\":\"SPEAKER_01\",\"start\":1.5,\"end\":2.5},\"tags\":[\"x\"]}");
    }

    private static <T extends Record> void assertRoundTrip(
            net.minestom.server.codec.StructCodec<T> codec, String vector) {
        T decoded = InvokeEnvelopes.decodeRecord(codec, vector);
        assertEquals(vector, InvokeEnvelopes.encodeRecord(codec, decoded));
    }
}
