package org.example.project1.tcp;

import org.example.project1.common.Messages;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * TCP client: sends {@code PING <text>}, prints {@code PONG <text>} reply.
 */
public final class TcpClient {

    private TcpClient() {
    }

    public static void main(String[] args) throws IOException {
        if (args.length < 2 || args.length > 3) {
            System.err.println("Usage: TcpClient <host> <port> [text]");
            System.err.println("Example: TcpClient 127.0.0.1 9000 hello");
            System.exit(1);
        }

        InetAddress host = InetAddress.getByName(args[0]);
        int port = Integer.parseInt(args[1]);
        String text = args.length == 3 ? args[2] : "hello";

        try (Socket socket = new Socket(host, port)) {
            BufferedWriter out = new BufferedWriter(
                    new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
            BufferedReader in = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));

            String request = Messages.ping(text);
            out.write(request);
            out.write('\n');
            out.flush();
            System.out.println("sent: " + request);

            String replyLine = in.readLine();
            if (replyLine == null) {
                throw new IOException("server closed connection without reply");
            }
            String replyText = Messages.requireType(replyLine, Messages.PONG);
            System.out.println("received: " + Messages.pong(replyText));
        }
    }
}
