package org.example.project1.common;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * Shared application protocol for TCP, UDP, and RAW demos.
 * Wire format: one UTF-8 line {@code PING <text>} or {@code PONG <text>}.
 */
public final class Messages {

    public static final String PING = "PING";
    public static final String PONG = "PONG";
    /** Max encoded line size on the wire. */
    public static final int MAX_PAYLOAD_BYTES = 512;

    private Messages() {
    }

    /**
     * Builds a request line {@code PING <text>}.
     *
     * @param text payload after the PING token
     */
    public static String ping(String text) {
        return encode(PING, text);
    }

    /**
     * Builds a reply line {@code PONG <text>}.
     *
     * @param text payload after the PONG token
     */
    public static String pong(String text) {
        return encode(PONG, text);
    }

    /**
     * Formats and validates a protocol line before sending.
     *
     * @param type {@code PING} or {@code PONG}
     * @param text single-line payload (no CR/LF)
     * @return ready-to-send line without trailing newline
     */
    public static String encode(String type, String text) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(text, "text");
        if (!PING.equals(type) && !PONG.equals(type)) {
            throw new IllegalArgumentException("unsupported message type: " + type);
        }
        if (text.contains("\n") || text.contains("\r")) {
            throw new IllegalArgumentException("payload must be a single line");
        }
        String line = type + " " + text;
        int bytes = line.getBytes(StandardCharsets.UTF_8).length;
        if (bytes > MAX_PAYLOAD_BYTES) {
            throw new IllegalArgumentException(
                    "message exceeds " + MAX_PAYLOAD_BYTES + " bytes (got " + bytes + ")");
        }
        return line;
    }

    /**
     * Parses a line and requires an exact message type (e.g. server expects PING).
     *
     * @param line full protocol line
     * @param expectedType required kind ({@link #PING} or {@link #PONG})
     * @return payload text only
     */
    public static String requireType(String line, String expectedType) {
        Parsed parsed = parse(line);
        if (!expectedType.equals(parsed.type())) {
            throw new IllegalArgumentException(
                    "expected " + expectedType + " but got " + parsed.type());
        }
        return parsed.text();
    }

    /**
     * Splits a received line into type + payload and rejects unknown formats.
     *
     * @param line wire line without trailing newline
     * @return parsed type and text
     */
    public static Parsed parse(String line) {
        Objects.requireNonNull(line, "line");
        String trimmed = line.strip();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("empty message");
        }
        int bytes = trimmed.getBytes(StandardCharsets.UTF_8).length;
        if (bytes > MAX_PAYLOAD_BYTES) {
            throw new IllegalArgumentException("message too long: " + bytes + " bytes");
        }
        int space = trimmed.indexOf(' ');
        if (space <= 0 || space == trimmed.length() - 1) {
            throw new IllegalArgumentException("expected '<TYPE> <text>', got: " + trimmed);
        }
        String type = trimmed.substring(0, space);
        String text = trimmed.substring(space + 1);
        if (!PING.equals(type) && !PONG.equals(type)) {
            throw new IllegalArgumentException("unsupported message type: " + type);
        }
        return new Parsed(type, text);
    }

    /**
     * Result of {@link #parse(String)}.
     *
     * @param type  PING or PONG token
     * @param text  payload after the token
     */
    public record Parsed(String type, String text) {
    }
}
