package org.example.project1.udp;

import org.example.project1.common.Messages;
import org.example.project1.common.Net;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;

/**
 * UDP echo server: one datagram = one {@code PING}, reply {@code PONG} to the sender.
 * CLI: {@code UdpServer <port> [bindHost]}.
 */
public final class UdpServer {

    private UdpServer() {
    }

    /**
     * Binds a UDP socket and replies to incoming PING datagrams forever.
     *
     * @param args {@code port} and optional {@code bindHost}
     */
    public static void main(String[] args) throws IOException {
        if (args.length < 1 || args.length > 2) {
            System.err.println("Usage: UdpServer <port> [bindHost]");
            System.err.println("Example: UdpServer 9001 127.0.0.1");
            System.err.println("Example: UdpServer 9001 ::1");
            System.exit(1);
        }

        int port = Integer.parseInt(args[0]); // L4 bind port
        InetAddress bindAddress = args.length == 2
                ? InetAddress.getByName(args[1])
                : InetAddress.getByName("127.0.0.1");

        byte[] buffer = new byte[Messages.MAX_PAYLOAD_BYTES];
        try (DatagramSocket socket = new DatagramSocket(port, bindAddress)) {
            System.out.println("UDP server listening on " + socket.getLocalSocketAddress()
                    + " (" + Net.familyLabel(bindAddress) + ")");
            while (true) {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet); // fills sender address + port
                try {
                    handlePacket(socket, packet);
                } catch (IllegalArgumentException ex) {
                    System.err.println("protocol error from "
                            + packet.getSocketAddress() + ": " + ex.getMessage());
                }
            }
        }
    }

    /**
     * Validates a received PING datagram and sends a PONG back to its source address/port.
     *
     * @param socket bound UDP socket used to send the reply
     * @param packet received datagram (offset/length already set by {@code receive})
     */
    private static void handlePacket(DatagramSocket socket, DatagramPacket packet)
            throws IOException {
        String line = new String(packet.getData(), packet.getOffset(), packet.getLength(),
                StandardCharsets.UTF_8).strip();
        System.out.println("from " + packet.getSocketAddress()
                + " (" + Net.familyLabel(packet.getAddress()) + "): " + line);

        String text = Messages.requireType(line, Messages.PING);
        byte[] replyBytes = (Messages.pong(text) + "\n").getBytes(StandardCharsets.UTF_8);
        // reply to packet.getAddress()/getPort() — no connection state
        DatagramPacket reply = new DatagramPacket(
                replyBytes, replyBytes.length, packet.getAddress(), packet.getPort());
        socket.send(reply);
        System.out.println("replied: " + Messages.pong(text));
    }
}
