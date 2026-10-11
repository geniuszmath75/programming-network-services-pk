package org.example.project1.udp;

import org.example.project1.common.Messages;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;

/**
 * UDP echo server: one datagram = one {@code PING} line, reply {@code PONG} to sender.
 */
public final class UdpServer {

    private UdpServer() {
    }

    public static void main(String[] args) throws IOException {
        if (args.length < 1 || args.length > 2) {
            System.err.println("Usage: UdpServer <port> [bindHost]");
            System.err.println("Example: UdpServer 9001 127.0.0.1");
            System.exit(1);
        }

        int port = Integer.parseInt(args[0]);
        InetAddress bindAddress = args.length == 2
                ? InetAddress.getByName(args[1])
                : InetAddress.getByName("127.0.0.1");

        byte[] buffer = new byte[Messages.MAX_PAYLOAD_BYTES];
        try (DatagramSocket socket = new DatagramSocket(port, bindAddress)) {
            System.out.println("UDP server listening on " + socket.getLocalSocketAddress());
            while (true) {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);
                try {
                    handlePacket(socket, packet);
                } catch (IllegalArgumentException ex) {
                    System.err.println("protocol error from "
                            + packet.getSocketAddress() + ": " + ex.getMessage());
                }
            }
        }
    }

    private static void handlePacket(DatagramSocket socket, DatagramPacket packet)
            throws IOException {
        String line = new String(packet.getData(), packet.getOffset(), packet.getLength(),
                StandardCharsets.UTF_8).strip();
        System.out.println("from " + packet.getSocketAddress() + ": " + line);

        String text = Messages.requireType(line, Messages.PING);
        byte[] replyBytes = (Messages.pong(text) + "\n").getBytes(StandardCharsets.UTF_8);
        DatagramPacket reply = new DatagramPacket(
                replyBytes, replyBytes.length, packet.getAddress(), packet.getPort());
        socket.send(reply);
        System.out.println("replied: " + Messages.pong(text));
    }
}
