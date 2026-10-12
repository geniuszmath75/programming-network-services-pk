package org.example.project1.raw;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteOrder;
import java.time.Duration;
import java.util.Arrays;
import java.util.Objects;

/**
 * Thin SOCK_RAW wrapper: kernel builds the IPv4 header on send; receives include the header.
 */
public final class RawSocket implements AutoCloseable {

    private static final int MAX_PACKET = 1500;
    private static final ValueLayout.OfShort SIN_FAMILY =
            ValueLayout.JAVA_SHORT.withOrder(ByteOrder.nativeOrder());

    private final int fd;
    private boolean closed;

    private RawSocket(int fd) {
        this.fd = fd;
    }

    public static RawSocket open() {
        int fd = Libc.socket(Libc.AF_INET, Libc.SOCK_RAW, Libc.IPPROTO_EXPERIMENTAL);
        if (fd < 0) {
            throw new IllegalStateException(
                    "socket(AF_INET, SOCK_RAW, 253) failed (need CAP_NET_RAW / root?)");
        }
        return new RawSocket(fd);
    }

    public void setReceiveTimeout(Duration timeout) {
        Objects.requireNonNull(timeout, "timeout");
        if (timeout.isNegative()) {
            throw new IllegalArgumentException("timeout must be non-negative");
        }
        long millis = timeout.toMillis();
        long sec = millis / 1000;
        long usec = (millis % 1000) * 1000;
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment tv = arena.allocate(16);
            tv.set(ValueLayout.JAVA_LONG, 0, sec);
            tv.set(ValueLayout.JAVA_LONG, 8, usec);
            int rc = Libc.setsockopt(fd, Libc.SOL_SOCKET, Libc.SO_RCVTIMEO, tv, 16);
            if (rc != 0) {
                throw new IllegalStateException("setsockopt(SO_RCVTIMEO) failed: " + rc);
            }
        }
    }

    /**
     * Sends application payload only; Linux adds the IPv4 header for protocol 253.
     */
    public void send(Inet4Address destination, byte[] payload) {
        Objects.requireNonNull(destination, "destination");
        Objects.requireNonNull(payload, "payload");
        if (payload.length == 0) {
            throw new IllegalArgumentException("payload must not be empty");
        }
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment buf = arena.allocate(payload.length);
            MemorySegment.copy(payload, 0, buf, ValueLayout.JAVA_BYTE, 0, payload.length);
            MemorySegment addr = sockaddrIn(arena, destination);
            long sent = Libc.sendto(fd, buf, payload.length, 0, addr, Libc.SOCKADDR_IN_SIZE);
            if (sent < 0) {
                throw new IllegalStateException("sendto failed");
            }
        }
    }

    public Received receive() {
        byte[] packet = new byte[MAX_PACKET];
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment buf = arena.allocate(MAX_PACKET);
            MemorySegment addr = arena.allocate(Libc.SOCKADDR_IN_SIZE);
            MemorySegment addrLen = arena.allocate(ValueLayout.JAVA_INT);
            addrLen.set(ValueLayout.JAVA_INT, 0, Libc.SOCKADDR_IN_SIZE);
            long n = Libc.recvfrom(fd, buf, MAX_PACKET, 0, addr, addrLen);
            if (n < 0) {
                throw new IllegalStateException("recvfrom failed or timed out");
            }
            int length = Math.toIntExact(n);
            MemorySegment.copy(buf, ValueLayout.JAVA_BYTE, 0, packet, 0, length);
            IpPacket ip = IpPacket.parse(packet, length);
            return new Received(ip, Arrays.copyOf(packet, length));
        }
    }

    private static MemorySegment sockaddrIn(Arena arena, Inet4Address address) {
        MemorySegment addr = arena.allocate(Libc.SOCKADDR_IN_SIZE);
        addr.fill((byte) 0);
        addr.set(SIN_FAMILY, 0, (short) Libc.AF_INET);
        // sin_port unused for raw protocol sockets
        addr.set(ValueLayout.JAVA_SHORT.withOrder(ByteOrder.BIG_ENDIAN), 2, (short) 0);
        byte[] octets = address.getAddress();
        for (int i = 0; i < 4; i++) {
            addr.set(ValueLayout.JAVA_BYTE, 4 + i, octets[i]);
        }
        return addr;
    }

    static Inet4Address requireInet4(String host) throws UnknownHostException {
        InetAddress address = InetAddress.getByName(host);
        if (!(address instanceof Inet4Address inet4)) {
            throw new IllegalArgumentException("raw demo supports IPv4 only: " + host);
        }
        return inet4;
    }

    @Override
    public void close() {
        if (!closed) {
            closed = true;
            Libc.close(fd);
        }
    }

    public record Received(IpPacket packet, byte[] rawBytes) {
    }
}
