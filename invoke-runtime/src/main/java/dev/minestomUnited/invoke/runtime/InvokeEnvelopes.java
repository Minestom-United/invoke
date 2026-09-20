package dev.minestomUnited.invoke.runtime;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minestom.server.codec.Codec;
import net.minestom.server.codec.Result;
import net.minestom.server.codec.StructCodec;
import net.minestom.server.codec.Transcoder;

import java.util.Objects;

/**
 * Envelope helpers shared by the registry and the client factory. Success bodies
 * look like {"result":{...}} (or {"result":null} for void), failures like
 * {"error":{"code":N,"message":"..."}}. Record payloads always go through the
 * record's static CODEC, never through ad-hoc JSON code.
 */
public final class InvokeEnvelopes {
    private InvokeEnvelopes() {
    }

    /**
     * A parsed envelope: either the result payload as raw JSON text, or a code
     * plus message. Clients throw on Error and decode the payload on Result.
     */
    public sealed interface Envelope permits Envelope.Result, Envelope.Error {
        /**
         * Success half of an envelope.
         *
         * @param payloadJson the raw JSON under "result", "null" for void methods
         */
        record Result(String payloadJson) implements Envelope {
            public Result {
                Objects.requireNonNull(payloadJson, "payloadJson");
            }
        }

        /**
         * Failure half of an envelope.
         *
         * @param code HTTP-style status code from the server
         * @param message human-readable detail, safe to show the caller
         */
        record Error(int code, String message) implements Envelope {
            public Error {
                Objects.requireNonNull(message, "message");
            }
        }
    }

    /**
     * Wraps an already-encoded payload object in a result envelope.
     *
     * @param payloadJson the encoded result object, e.g. {"status":"ok"}
     * @return the full envelope body {"result":{...}}
     */
    public static String encodeResult(String payloadJson) {
        Objects.requireNonNull(payloadJson, "payloadJson");
        JsonObject wrapper = new JsonObject();
        wrapper.add("result", JsonParser.parseString(payloadJson));
        return wrapper.toString();
    }

    /**
     * Returns the fixed void-result body. Byte-exact by contract: {"result":null}.
     *
     * @return the void success envelope
     */
    public static String encodeVoidResult() {
        return "{\"result\":null}";
    }

    /**
     * Builds an error envelope body from a code and message.
     *
     * @param code HTTP-style status code
     * @param message human-readable detail, JSON-escaped by the encoder
     * @return the full envelope body {"error":{"code":N,"message":"..."}}
     */
    public static String encodeError(int code, String message) {
        Objects.requireNonNull(message, "message");
        JsonObject body = new JsonObject();
        body.addProperty("code", code);
        body.addProperty("message", message);
        JsonObject wrapper = new JsonObject();
        wrapper.add("error", body);
        return wrapper.toString();
    }

    /**
     * Parses an envelope body. A missing "result" and "error" (or a code/message
     * of the wrong shape) throws InvokeException(500): a malformed envelope is a
     * server bug, never a client one.
     *
     * @param bodyJson the raw response body
     * @return the parsed Result or Error
     * @throws InvokeException with code 500 on malformed envelopes
     */
    public static Envelope decode(String bodyJson) {
        Objects.requireNonNull(bodyJson, "bodyJson");
        JsonElement root;
        try {
            root = JsonParser.parseString(bodyJson);
        } catch (Exception e) {
            throw new InvokeException(500, "malformed envelope");
        }
        if (!(root instanceof JsonObject object)) {
            throw new InvokeException(500, "malformed envelope");
        }
        if (object.has("result")) {
            JsonElement payload = object.get("result");
            return new Envelope.Result(payload == null ? "null" : payload.toString());
        }
        if (object.has("error")) {
            JsonElement error = object.get("error");
            if (error instanceof JsonObject errorObject
                    && errorObject.has("code")
                    && errorObject.has("message")) {
                try {
                    int code = errorObject.get("code").getAsInt();
                    String message = errorObject.get("message").getAsString();
                    return new Envelope.Error(code, message);
                } catch (Exception e) {
                    throw new InvokeException(500, "malformed envelope");
                }
            }
            throw new InvokeException(500, "malformed envelope");
        }
        throw new InvokeException(500, "malformed envelope");
    }

    /**
     * Encodes a record to its flat JSON object via its CODEC. Throws
     * InvokeException(500) when the codec reports a failure, since a well-formed
     * record should always encode.
     *
     * @param codec the record's static CODEC, e.g. ReviewArgs.CODEC
     * @param value the record to encode
     * @param <T> the record type
     * @return the flat JSON object, no envelope wrapper
     * @throws InvokeException with code 500 on encode failure
     */
    public static <T extends Record> String encodeRecord(StructCodec<T> codec, T value) {
        Objects.requireNonNull(codec, "codec");
        Objects.requireNonNull(value, "value");
        Result<JsonElement> result = codec.encode(Transcoder.JSON, value);
        if (result instanceof Result.Ok<JsonElement>(JsonElement value1)) {
            return value1.toString();
        }
        throw new InvokeException(500, "encode failure: " + ((Result.Error<JsonElement>) result).message());
    }

    /**
     * Decodes a flat JSON object into a record via its CODEC. Throws
     * InvokeException(400): undecodable args mean the caller sent the wrong
     * shape. Note an explicit JSON null for an optional field fails here, only a
     * missing key decodes to null.
     *
     * @param codec the record's static CODEC, e.g. ReviewArgs.CODEC
     * @param json the flat JSON object
     * @param <T> the record type
     * @return the decoded record
     * @throws InvokeException with code 400 on bad shape
     */
    public static <T extends Record> T decodeRecord(StructCodec<T> codec, String json) {
        Objects.requireNonNull(codec, "codec");
        Objects.requireNonNull(json, "json");
        JsonElement element;
        try {
            element = JsonParser.parseString(json);
        } catch (Exception e) {
            throw new InvokeException(400, "bad args shape");
        }
        Result<T> result = codec.decode(Transcoder.JSON, element);
        if (result instanceof Result.Ok<T>(T value)) {
            return value;
        }
        throw new InvokeException(400, "bad args shape");
    }

    static StructCodec<ErrorBody> errorBodyCodec() {
        return ErrorBody.CODEC;
    }

    record ErrorBody(int code, String message) {
        static final StructCodec<ErrorBody> CODEC = StructCodec.struct(
                "code", Codec.INT, ErrorBody::code,
                "message", Codec.STRING, ErrorBody::message,
                ErrorBody::new);
    }
}
