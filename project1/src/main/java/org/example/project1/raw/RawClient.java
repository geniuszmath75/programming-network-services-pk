package org.example.project1.raw;

import org.example.project1.common.Messages;

import java.net.Inet4Address;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * RAW client on IP protocol 253: send {@code PING}, wait for {@code PONG}.
 * On loopback, skips the echoed outbound PING until a real PONG arrives.
 */
public final class RawClient {

    private static final Duration TIMEOUT = Duration.ofSeconds(3);

    private RawClient() {
    }

    /**
     * Sends one PING to the given IPv4 host and prints the matching PONG.
     *
     * @param args {@code ipv4-host} and optional payload {@code text}
     */
    public static void main(String[] args) throws Exception {
        if (args.length < 1 || args.length > 2) {
            System.err.println("Usage: RawClient <ipv4-host> [text]");
            System.err.println("Example: RawClient 127.0.0.1 hello");
            System.exit(1);
        }

        Inet4Address host = RawSocket.requireInet4(args[0]);
        String text = args.length == 2 ? args[1] : "hello";
        byte[] request = Messages.ping(text).getBytes(StandardCharsets.UTF_8);

        try (RawSocket socket = RawSocket.open()) {
            socket.setReceiveTimeout(TIMEOUT);
            socket.send(host, request);
            System.out.println("sent: " + Messages.ping(text) + " -> " + host.getHostAddress());

            long deadline = System.nanoTime() + TIMEOUT.toNanos();
            while (System.nanoTime() < deadline) {
                RawSocket.Received received;
                try {
                    received = socket.receive();
                } catch (IllegalStateException ex) {
                    throw new IllegalStateException(
                            "timed out waiting for PONG from " + host.getHostAddress(), ex);
                }
                IpPacket ip = received.packet();
                if (ip.protocol() != Libc.IPPROTO_EXPERIMENTAL) {
                    continue;
                }
                // On loopback, own outbound PING often arrives first — wait for PONG.
                String line = new String(ip.payload(), StandardCharsets.UTF_8).strip();
                try {
                    String replyText = Messages.requireType(line, Messages.PONG);
                    System.out.println("received from " + ip.source() + ": "
                            + Messages.pong(replyText));
                    return;
                } catch (IllegalArgumentException ignored) {
                    // not PONG yet
                }
            }
            throw new IllegalStateException("timed out waiting for PONG from " + host.getHostAddress());
        }
    }
}
