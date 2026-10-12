package org.example.project1.tcp;

import org.example.project1.common.Messages;
import org.example.project1.common.Net;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * Single-threaded TCP echo server: reads {@code PING <text>}, replies {@code PONG <text>}.
 * Bind host may be IPv4 ({@code 127.0.0.1}) or IPv6 ({@code ::1}).
 */
public final class TcpServer {

    private TcpServer() {
    }

    public static void main(String[] args) throws IOException {
        if (args.length < 1 || args.length > 2) {
            System.err.println("Usage: TcpServer <port> [bindHost]");
            System.err.println("Example: TcpServer 9000 127.0.0.1");
            System.err.println("Example: TcpServer 9000 ::1");
            System.exit(1);
        }

        int port = Integer.parseInt(args[0]);
        InetAddress bindAddress = args.length == 2
                ? InetAddress.getByName(args[1])
                : InetAddress.getByName("127.0.0.1");

        try (ServerSocket serverSocket = new ServerSocket(port, 50, bindAddress)) {
            System.out.println("TCP server listening on " + serverSocket.getLocalSocketAddress()
                    + " (" + Net.familyLabel(bindAddress) + ")");
            while (true) {
                try (Socket socket = serverSocket.accept()) {
                    handleClient(socket);
                } catch (IllegalArgumentException ex) {
                    System.err.println("protocol error: " + ex.getMessage());
                } catch (IOException ex) {
                    System.err.println("client I/O error: " + ex.getMessage());
                }
            }
        }
    }

    private static void handleClient(Socket socket) throws IOException {
        System.out.println("accepted " + socket.getRemoteSocketAddress()
                + " (" + Net.familyLabel(socket.getInetAddress()) + ")");
        BufferedReader in = new BufferedReader(
                new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        BufferedWriter out = new BufferedWriter(
                new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));

        String line = in.readLine();
        if (line == null) {
            return;
        }
        String text = Messages.requireType(line, Messages.PING);
        String reply = Messages.pong(text);
        out.write(reply);
        out.write('\n');
        out.flush();
        System.out.println("replied: " + reply);
    }
}
