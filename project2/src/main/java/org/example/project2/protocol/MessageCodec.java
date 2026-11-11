package org.example.project2.protocol;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Objects;

/**
 * Encodes and decodes the line-framed application protocol.
 *
 * <p>Wire format:</p>
 * <ul>
 *     <li>{@code PING requestId base64Payload}</li>
 *     <li>{@code PONG requestId base64Payload}</li>
 *     <li>{@code ERROR errorCode base64Message}</li>
 * </ul>
 *
 * <p>Every frame ends with LF. CRLF is accepted for interoperability. The
 * maximum frame size is enforced while reading, before parsing or allocating
 * a large payload.</p>
 */
public final class MessageCodec {

    public static final int MAX_FRAME_BYTES = 4096;

    private MessageCodec() {
    }

    /**
     * Reads one frame.
     *
     * @return the decoded message, or {@code null} when the stream reached EOF
     * before a new frame started
     */
    public static ProtocolMessage read(InputStream input)
            throws IOException, ProtocolException {
        Objects.requireNonNull(input, "input");

        ByteArrayOutputStream frame = new ByteArrayOutputStream();
        boolean started = false;

        while (true) {
            int value = input.read();
            if (value == -1) {
                if (!started) {
                    return null;
                }
                throw new ProtocolException(
                        ProtocolException.Code.INCOMPLETE_FRAME,
                        "frame ended before LF");
            }
            if (value == '\n') {
                break;
            }
            if (frame.size() >= MAX_FRAME_BYTES) {
                throw new ProtocolException(
                        ProtocolException.Code.FRAME_TOO_LARGE,
                        "frame exceeds the maximum size");
            }

            started = true;
            frame.write(value);
        }

        byte[] rawFrame = frame.toByteArray();
        if (rawFrame.length > 0 && rawFrame[rawFrame.length - 1] == '\r') {
            rawFrame = java.util.Arrays.copyOf(rawFrame, rawFrame.length - 1);
        }
        if (rawFrame.length == 0) {
            throw new ProtocolException(
                    ProtocolException.Code.INVALID_FORMAT,
                    "frame is empty");
        }

        return decode(decodeUtf8(rawFrame));
    }

    /**
     * Writes one frame and flushes the stream so request-response callers do
     * not wait for an unrelated later message.
     */
    public static void write(OutputStream output, ProtocolMessage message)
            throws IOException, ProtocolException {
        Objects.requireNonNull(output, "output");
        byte[] frame = encode(message).getBytes(StandardCharsets.US_ASCII);
        output.write(frame);
        output.write('\n');
        output.flush();
    }

    /**
     * Encodes one message without its trailing LF.
     */
    public static String encode(ProtocolMessage message) throws ProtocolException {
        Objects.requireNonNull(message, "message");

        String line = switch (message) {
            case ProtocolMessage.Ping ping ->
                    "PING " + ping.requestId() + " " + encodeBase64(ping.payload());
            case ProtocolMessage.Pong pong ->
                    "PONG " + pong.requestId() + " " + encodeBase64(pong.payload());
            case ProtocolMessage.Error error ->
                    "ERROR " + error.code() + " "
                            + encodeBase64(error.message().getBytes(StandardCharsets.UTF_8));
        };

        int frameBytes = line.getBytes(StandardCharsets.US_ASCII).length;
        if (frameBytes > MAX_FRAME_BYTES) {
            throw new ProtocolException(
                    ProtocolException.Code.FRAME_TOO_LARGE,
                    "encoded frame exceeds the maximum size");
        }
        return line;
    }

    /**
     * Decodes one frame without its trailing LF.
     */
    public static ProtocolMessage decode(String line) throws ProtocolException {
        Objects.requireNonNull(line, "line");
        if (line.isEmpty()) {
            throw new ProtocolException(
                    ProtocolException.Code.INVALID_FORMAT,
                    "frame is empty");
        }
        if (line.getBytes(StandardCharsets.UTF_8).length > MAX_FRAME_BYTES) {
            throw new ProtocolException(
                    ProtocolException.Code.FRAME_TOO_LARGE,
                    "frame exceeds the maximum size");
        }

        String[] fields = line.split(" ", -1);
        if (fields.length != 3) {
            throw new ProtocolException(
                    ProtocolException.Code.INVALID_FORMAT,
                    "frame must contain exactly three fields");
        }

        return switch (fields[0]) {
            case "PING" -> new ProtocolMessage.Ping(
                    parseRequestId(fields[1]),
                    decodePayload(fields[2]));
            case "PONG" -> new ProtocolMessage.Pong(
                    parseRequestId(fields[1]),
                    decodePayload(fields[2]));
            case "ERROR" -> decodeError(fields[1], fields[2]);
            default -> throw new ProtocolException(
                    ProtocolException.Code.INVALID_MESSAGE_TYPE,
                    "unsupported message type");
        };
    }

    private static ProtocolMessage.Error decodeError(String code, String encodedMessage)
            throws ProtocolException {
        try {
            String message = decodeUtf8(decodePayload(encodedMessage));
            return new ProtocolMessage.Error(code, message);
        } catch (IllegalArgumentException exception) {
            throw new ProtocolException(
                    ProtocolException.Code.INVALID_ERROR,
                    "invalid error message");
        }
    }

    private static long parseRequestId(String value) throws ProtocolException {
        try {
            long requestId = Long.parseLong(value);
            if (requestId < 1) {
                throw new NumberFormatException();
            }
            return requestId;
        } catch (NumberFormatException exception) {
            throw new ProtocolException(
                    ProtocolException.Code.INVALID_REQUEST_ID,
                    "request id must be a positive integer");
        }
    }

    private static byte[] decodePayload(String encoded) throws ProtocolException {
        try {
            byte[] payload = Base64.getDecoder().decode(encoded);
            if (payload.length > ProtocolMessage.MAX_PAYLOAD_BYTES) {
                throw new ProtocolException(
                        ProtocolException.Code.INVALID_PAYLOAD,
                        "payload exceeds the maximum size");
            }
            return payload;
        } catch (IllegalArgumentException exception) {
            throw new ProtocolException(
                    ProtocolException.Code.INVALID_PAYLOAD,
                    "payload is not valid Base64");
        }
    }

    private static String encodeBase64(byte[] value) {
        return Base64.getEncoder().encodeToString(value);
    }

    private static String decodeUtf8(byte[] value) throws ProtocolException {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(value))
                    .toString();
        } catch (CharacterCodingException exception) {
            throw new ProtocolException(
                    ProtocolException.Code.INVALID_UTF8,
                    "frame is not valid UTF-8");
        }
    }
}
