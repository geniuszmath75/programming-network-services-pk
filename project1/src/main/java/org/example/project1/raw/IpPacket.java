package org.example.project1.raw;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;

/**
 * Minimal IPv4 header view for SOCK_RAW receives (kernel prepends the IP header).
 */
public final class IpPacket {

    private static final int MIN_HEADER_BYTES = 20;

    private final int version;
    private final int headerLength;
    private final int protocol;
    private final Inet4Address source;
    private final Inet4Address destination;
    private final byte[] payload;

    private IpPacket(
            int version,
            int headerLength,
            int protocol,
            Inet4Address source,
            Inet4Address destination,
            byte[] payload) {
        this.version = version;
        this.headerLength = headerLength;
        this.protocol = protocol;
        this.source = source;
        this.destination = destination;
        this.payload = payload;
    }

    /**
     * Parses an IPv4 datagram from a raw receive buffer into header fields + payload.
     *
     * @param packet raw bytes from {@code recvfrom} (IP header first)
     * @param length number of valid bytes in {@code packet}
     */
    public static IpPacket parse(byte[] packet, int length) {
        if (length < MIN_HEADER_BYTES) {
            throw new IllegalArgumentException("packet shorter than IPv4 header: " + length);
        }
        int version = (packet[0] & 0xF0) >>> 4; // high nibble
        int ihl = packet[0] & 0x0F;             // header length in 32-bit words
        int headerLength = ihl * 4;
        if (version != 4) {
            throw new IllegalArgumentException("not IPv4 (version=" + version + ")");
        }
        if (headerLength < MIN_HEADER_BYTES || headerLength > length) {
            throw new IllegalArgumentException("invalid IHL: " + ihl);
        }
        int totalLength = ((packet[2] & 0xFF) << 8) | (packet[3] & 0xFF); // network order
        int usable = Math.min(length, totalLength > 0 ? totalLength : length);
        if (usable < headerLength) {
            throw new IllegalArgumentException("total length shorter than header");
        }
        int protocol = packet[9] & 0xFF;        // Protocol field
        Inet4Address source = ipv4(packet, 12); // Source Address
        Inet4Address destination = ipv4(packet, 16); // Destination Address
        byte[] payload = Arrays.copyOfRange(packet, headerLength, usable);
        return new IpPacket(version, headerLength, protocol, source, destination, payload);
    }

    /**
     * Reads a 4-byte IPv4 address from the header.
     *
     * @param packet IP datagram bytes
     * @param offset start of the address field (12 = src, 16 = dst)
     */
    private static Inet4Address ipv4(byte[] packet, int offset) {
        try {
            return (Inet4Address) InetAddress.getByAddress(Arrays.copyOfRange(packet, offset, offset + 4));
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException("invalid IPv4 address", e);
        }
    }

    /** IP version (should be 4). */
    public int version() {
        return version;
    }

    /** IPv4 header length in bytes (IHL × 4). */
    public int headerLength() {
        return headerLength;
    }

    /** IP Protocol field (e.g. 253 for this demo). */
    public int protocol() {
        return protocol;
    }

    /** Source IPv4 address from the header. */
    public Inet4Address source() {
        return source;
    }

    /** Destination IPv4 address from the header. */
    public Inet4Address destination() {
        return destination;
    }

    /** Bytes after the IP header (application payload). */
    public byte[] payload() {
        return payload;
    }
}
