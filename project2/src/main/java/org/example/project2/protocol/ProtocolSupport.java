package org.example.project2.protocol;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * Shared request/response behavior used by the TCP and QUIC demos.
 */
public final class ProtocolSupport {

    private static final long REQUEST_ID = 1;

    private ProtocolSupport() {
    }

    /**
     * Sends one PING request, reads its response, and prints the result.
     */
    public static void requestAndPrint(
            InputStream input,
            OutputStream output,
            String payload)
            throws IOException {
        Objects.requireNonNull(payload, "payload");
        MessageCodec.write(
                output,
                new ProtocolMessage.Ping(
                        REQUEST_ID,
                        payload.getBytes(StandardCharsets.UTF_8)));

        ProtocolMessage response = MessageCodec.read(input);
        if (response == null) {
            throw new IOException("server closed the stream without a response");
        }
        printResponse(response);
    }

    /**
     * Processes one request. Returns {@code true} when the caller may read
     * another request from the same stream.
     */
    public static boolean handleRequest(InputStream input, OutputStream output)
            throws IOException {
        ProtocolMessage request;
        try {
            request = MessageCodec.read(input);
        } catch (ProtocolException exception) {
            writeError(output, exception.code().name());
            return false;
        }

        if (request == null) {
            return false;
        }
        if (request instanceof ProtocolMessage.Ping ping) {
            MessageCodec.write(
                    output,
                    new ProtocolMessage.Pong(ping.requestId(), ping.payload()));
        } else {
            MessageCodec.write(
                    output,
                    new ProtocolMessage.Error(
                            "INVALID_MESSAGE_TYPE",
                            "expected PING"));
        }
        return true;
    }

    private static void printResponse(ProtocolMessage response) throws IOException {
        if (response instanceof ProtocolMessage.Pong pong && pong.requestId() == REQUEST_ID) {
            String payload = new String(pong.payload(), StandardCharsets.UTF_8);
            System.out.printf("PONG requestId=%d payload=%s%n", pong.requestId(), payload);
            return;
        }
        if (response instanceof ProtocolMessage.Error error) {
            throw new IOException("server rejected request: " + error.code());
        }
        throw new IOException("unexpected response message");
    }

    private static void writeError(OutputStream output, String code) {
        try {
            MessageCodec.write(
                    output,
                    new ProtocolMessage.Error(code, "invalid protocol frame"));
        } catch (IOException | RuntimeException ignored) {
            // The peer may have closed or reset the stream already.
        }
    }
}
