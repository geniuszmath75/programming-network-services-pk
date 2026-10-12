package org.example.project1.raw;

import org.example.project1.common.Messages;

import java.net.Inet4Address;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * SOCK_RAW client (IP protocol 253): sends {@code PING}, waits for {@code PONG}.
 * Requires {@code CAP_NET_RAW} (or root). IPv4 only.
 */
public final class RawClient {

    private static final Duration TIMEOUT = Duration.ofSeconds(3);

    private RawClient() {
    }

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
                // Local raw sockets often see our own outbound PING first — skip until PONG.
                String line = new String(ip.payload(), StandardCharsets.UTF_8).strip();
                try {
                    String replyText = Messages.requireType(line, Messages.PONG);
                    System.out.println("received from " + ip.source() + ": "
                            + Messages.pong(replyText));
                    return;
                } catch (IllegalArgumentException ignored) {
                    // not a PONG (e.g. echo of our PING)
                }
            }
            throw new IllegalStateException("timed out waiting for PONG from " + host.getHostAddress());
        }
    }
}
