package org.example.project1.raw;

import org.example.project1.common.Messages;

import java.nio.charset.StandardCharsets;

/**
 * SOCK_RAW server (IP protocol 253): replies {@code PONG} to {@code PING} payloads.
 * Requires {@code CAP_NET_RAW} (or root). IPv4 only.
 */
public final class RawServer {

    private RawServer() {
    }

    public static void main(String[] args) {
        if (args.length != 0) {
            System.err.println("Usage: RawServer");
            System.err.println("Listens for IP protocol 253 packets and replies to PING.");
            System.exit(1);
        }

        try (RawSocket socket = RawSocket.open()) {
            System.out.println("RAW server listening (IP proto "
                    + Libc.IPPROTO_EXPERIMENTAL + ", SOCK_RAW)");
            while (true) {
                try {
                    handleOne(socket);
                } catch (IllegalArgumentException ex) {
                    System.err.println("ignored packet: " + ex.getMessage());
                } catch (IllegalStateException ex) {
                    System.err.println("receive error: " + ex.getMessage());
                }
            }
        }
    }

    private static void handleOne(RawSocket socket) {
        RawSocket.Received received = socket.receive();
        IpPacket ip = received.packet();
        if (ip.protocol() != Libc.IPPROTO_EXPERIMENTAL) {
            return;
        }
        String line = new String(ip.payload(), StandardCharsets.UTF_8).strip();
        if (line.isEmpty()) {
            return;
        }
        Messages.Parsed parsed;
        try {
            parsed = Messages.parse(line);
        } catch (IllegalArgumentException ex) {
            System.err.println("from " + ip.source() + ": bad payload: " + ex.getMessage());
            return;
        }
        if (!Messages.PING.equals(parsed.type())) {
            return;
        }
        System.out.println("from " + ip.source() + ": " + line);
        byte[] reply = Messages.pong(parsed.text()).getBytes(StandardCharsets.UTF_8);
        socket.send(ip.source(), reply);
        System.out.println("replied: " + Messages.pong(parsed.text()));
    }
}
