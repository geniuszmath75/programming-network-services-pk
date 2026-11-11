package org.example.project2.protocol;

import java.io.IOException;
import java.util.Objects;

/**
 * Signals a malformed or incomplete application-protocol frame.
 */
public final class ProtocolException extends IOException {

    public enum Code {
        FRAME_TOO_LARGE,
        INVALID_FORMAT,
        INVALID_MESSAGE_TYPE,
        INVALID_REQUEST_ID,
        INVALID_PAYLOAD,
        INVALID_ERROR,
        INVALID_UTF8,
        INCOMPLETE_FRAME
    }

    private final Code code;

    public ProtocolException(Code code, String message) {
        super(message);
        this.code = Objects.requireNonNull(code, "code");
    }

    public Code code() {
        return code;
    }
}
