package org.example.project1.raw;

import org.example.project1.common.Messages;

import java.nio.charset.StandardCharsets;

/**
 * RAW server on IP protocol 253: answer {@code PING} with {@code PONG}.
 * Needs {@code CAP_NET_RAW} (see {@code scripts/grant-caps.sh}). IPv4 only.
 */
public final class RawServer {

    private RawServer() {
    }

    /**
     * Opens a SOCK_RAW socket and serves PING requests until the process is killed.
     *
     * @param args must be empty (no CLI options)
     */
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

    /**
     * Receives one IP packet; if it carries a valid PING on proto 253, sends PONG
     * back to the IP source address.
     *
     * @param socket open raw socket
     */
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
            return; // ignore PONG / noise
        }
        System.out.println("from " + ip.source() + ": " + line);
        byte[] reply = Messages.pong(parsed.text()).getBytes(StandardCharsets.UTF_8);
        socket.send(ip.source(), reply); // reply to IP source
        System.out.println("replied: " + Messages.pong(parsed.text()));
    }
}
