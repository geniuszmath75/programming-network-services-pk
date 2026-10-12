package org.example.project1.raw;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;

/**
 * Minimal IPv4 header parse for SOCK_RAW receives (kernel includes the IP header).
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

    public static IpPacket parse(byte[] packet, int length) {
        if (length < MIN_HEADER_BYTES) {
            throw new IllegalArgumentException("packet shorter than IPv4 header: " + length);
        }
        int version = (packet[0] & 0xF0) >>> 4;
        int ihl = packet[0] & 0x0F;
        int headerLength = ihl * 4;
        if (version != 4) {
            throw new IllegalArgumentException("not IPv4 (version=" + version + ")");
        }
        if (headerLength < MIN_HEADER_BYTES || headerLength > length) {
            throw new IllegalArgumentException("invalid IHL: " + ihl);
        }
        int totalLength = ((packet[2] & 0xFF) << 8) | (packet[3] & 0xFF);
        int usable = Math.min(length, totalLength > 0 ? totalLength : length);
        if (usable < headerLength) {
            throw new IllegalArgumentException("total length shorter than header");
        }
        int protocol = packet[9] & 0xFF;
        Inet4Address source = ipv4(packet, 12);
        Inet4Address destination = ipv4(packet, 16);
        byte[] payload = Arrays.copyOfRange(packet, headerLength, usable);
        return new IpPacket(version, headerLength, protocol, source, destination, payload);
    }

    private static Inet4Address ipv4(byte[] packet, int offset) {
        try {
            return (Inet4Address) InetAddress.getByAddress(Arrays.copyOfRange(packet, offset, offset + 4));
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException("invalid IPv4 address", e);
        }
    }

    public int version() {
        return version;
    }

    public int headerLength() {
        return headerLength;
    }

    public int protocol() {
        return protocol;
    }

    public Inet4Address source() {
        return source;
    }

    public Inet4Address destination() {
        return destination;
    }

    public byte[] payload() {
        return payload;
    }
}
