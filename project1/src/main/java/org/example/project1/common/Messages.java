package org.example.project1.common;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * Minimal application protocol shared by TCP and UDP demos.
 * Wire format: one UTF-8 line — {@code PING <text>} or {@code PONG <text>}.
 */
public final class Messages {

    public static final String PING = "PING";
    public static final String PONG = "PONG";
    public static final int MAX_PAYLOAD_BYTES = 512;

    private Messages() {
    }

    public static String ping(String text) {
        return encode(PING, text);
    }

    public static String pong(String text) {
        return encode(PONG, text);
    }

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
     * Parses one protocol line (without trailing newline).
     *
     * @return payload text after the type token
     */
    public static String requireType(String line, String expectedType) {
        Parsed parsed = parse(line);
        if (!expectedType.equals(parsed.type())) {
            throw new IllegalArgumentException(
                    "expected " + expectedType + " but got " + parsed.type());
        }
        return parsed.text();
    }

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

    public record Parsed(String type, String text) {
    }
}
