package dev.minestomUnited.invoke.runtime;

import java.util.Objects;

/**
 * The only exception invoke throws. Carries the HTTP-style status code from the
 * error envelope, so callers switch on code() instead of parsing message text.
 * Impl code passes through untouched, unknown impl failures arrive as 500 with
 * the message "internal error" (stack traces never cross the wire).
 */
public final class InvokeException extends RuntimeException {
    private final int code;

    /**
     * Creates an error with a status code and message. Rejects a null message.
     *
     * @param code HTTP-style status code, e.g. 404 for a missing item
     * @param message human-readable detail, shown to the caller as-is
     */
    public InvokeException(int code, String message) {
        super(Objects.requireNonNull(message, "message"));
        this.code = code;
    }

    /**
     * Creates an error that wraps a lower-level failure such as a timeout or a
     * refused connection. The cause is kept for server-side logging only.
     *
     * @param code HTTP-style status code, usually 503 or 504 for transport faults
     * @param message human-readable detail, shown to the caller as-is
     * @param cause the underlying failure, never serialized into the envelope
     */
    public InvokeException(int code, String message, Throwable cause) {
        super(Objects.requireNonNull(message, "message"), cause);
        this.code = code;
    }

    /**
     * Returns the status code: the impl code on purpose-built errors, 400 for bad
     * args, 404 for unknown method, 500 for impl crashes, 503 for transport
     * failure, 504 for timeout.
     *
     * @return the error code carried by this exception
     */
    public int code() {
        return code;
    }
}
