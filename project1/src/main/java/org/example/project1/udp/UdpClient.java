package org.example.project1.udp;

import org.example.project1.common.Messages;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;

/**
 * UDP client: sends one {@code PING} datagram, prints {@code PONG} reply.
 */
public final class UdpClient {

    private static final int TIMEOUT_MS = 3_000;

    private UdpClient() {
    }

    public static void main(String[] args) throws IOException {
        if (args.length < 2 || args.length > 3) {
            System.err.println("Usage: UdpClient <host> <port> [text]");
            System.err.println("Example: UdpClient 127.0.0.1 9001 hello");
            System.exit(1);
        }

        InetAddress host = InetAddress.getByName(args[0]);
        int port = Integer.parseInt(args[1]);
        String text = args.length == 3 ? args[2] : "hello";

        byte[] requestBytes = (Messages.ping(text) + "\n").getBytes(StandardCharsets.UTF_8);
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setSoTimeout(TIMEOUT_MS);
            DatagramPacket request = new DatagramPacket(requestBytes, requestBytes.length, host, port);
            socket.send(request);
            System.out.println("sent: " + Messages.ping(text));

            byte[] buffer = new byte[Messages.MAX_PAYLOAD_BYTES];
            DatagramPacket response = new DatagramPacket(buffer, buffer.length);
            socket.receive(response);

            String replyLine = new String(
                    response.getData(), response.getOffset(), response.getLength(),
                    StandardCharsets.UTF_8).strip();
            String replyText = Messages.requireType(replyLine, Messages.PONG);
            System.out.println("received: " + Messages.pong(replyText));
        }
    }
}
