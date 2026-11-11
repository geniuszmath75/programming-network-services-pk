package org.example.project2.protocol;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;

/**
 * Messages exchanged by the TCP and QUIC demo transports.
 *
 * <p>Payloads are binary at the protocol level. The wire codec encodes them as
 * Base64 so that a message remains a single UTF-8 line.</p>
 */
public sealed interface ProtocolMessage
        permits ProtocolMessage.Ping, ProtocolMessage.Pong, ProtocolMessage.Error {

    int MAX_PAYLOAD_BYTES = 1024;
    int MAX_ERROR_MESSAGE_BYTES = 512;

    Kind kind();

    enum Kind {
        PING,
        PONG,
        ERROR
    }

    record Ping(long requestId, byte[] payload) implements ProtocolMessage {
        public Ping {
            requireRequestId(requestId);
            payload = copyPayload(payload);
        }

        @Override
        public Kind kind() {
            return Kind.PING;
        }

        @Override
        public byte[] payload() {
            return payload.clone();
        }

        @Override
        public boolean equals(Object other) {
            return this == other
                    || other instanceof Ping ping
                    && requestId == ping.requestId
                    && Arrays.equals(payload, ping.payload);
        }

        @Override
        public int hashCode() {
            return 31 * Long.hashCode(requestId) + Arrays.hashCode(payload);
        }
    }

    record Pong(long requestId, byte[] payload) implements ProtocolMessage {
        public Pong {
            requireRequestId(requestId);
            payload = copyPayload(payload);
        }

        @Override
        public Kind kind() {
            return Kind.PONG;
        }

        @Override
        public byte[] payload() {
            return payload.clone();
        }

        @Override
        public boolean equals(Object other) {
            return this == other
                    || other instanceof Pong pong
                    && requestId == pong.requestId
                    && Arrays.equals(payload, pong.payload);
        }

        @Override
        public int hashCode() {
            return 31 * Long.hashCode(requestId) + Arrays.hashCode(payload);
        }
    }

    record Error(String code, String message) implements ProtocolMessage {
        public Error {
            Objects.requireNonNull(code, "code");
            Objects.requireNonNull(message, "message");

            if (!code.matches("[A-Z][A-Z0-9_]{0,31}")) {
                throw new IllegalArgumentException("invalid error code");
            }
            if (message.getBytes(StandardCharsets.UTF_8).length > MAX_ERROR_MESSAGE_BYTES) {
                throw new IllegalArgumentException("error message is too large");
            }
        }

        @Override
        public Kind kind() {
            return Kind.ERROR;
        }
    }

    private static void requireRequestId(long requestId) {
        if (requestId < 1) {
            throw new IllegalArgumentException("request id must be positive");
        }
    }

    private static byte[] copyPayload(byte[] payload) {
        Objects.requireNonNull(payload, "payload");
        if (payload.length > MAX_PAYLOAD_BYTES) {
            throw new IllegalArgumentException("payload is too large");
        }
        return payload.clone();
    }
}
