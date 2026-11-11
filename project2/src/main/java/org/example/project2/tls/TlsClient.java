package org.example.project2.tls;

import org.example.project2.protocol.MessageCodec;
import org.example.project2.protocol.ProtocolException;
import org.example.project2.protocol.ProtocolMessage;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLSocket;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.util.Arrays;

/**
 * TLS 1.3 TCP client presenting a certificate issued by the demo CA.
 */
public final class TlsClient {

    private TlsClient() {
    }

    public static void main(String[] args) {
        try {
            run(args);
        } catch (IllegalArgumentException exception) {
            System.err.println("Invalid arguments: " + exception.getMessage());
            printUsage();
            System.exit(2);
        } catch (IllegalStateException exception) {
            System.err.println("TLS configuration error: " + exception.getMessage());
            System.exit(2);
        } catch (SSLHandshakeException exception) {
            System.err.println("TLS handshake failed: certificate was not accepted");
            System.exit(1);
        } catch (SocketTimeoutException exception) {
            System.err.println("TLS connection timed out");
            System.exit(1);
        } catch (GeneralSecurityException | IOException exception) {
            System.err.println("TLS client failed: " + exception.getClass().getSimpleName() + exception.getMessage());
            System.exit(1);
        }
    }

    private static void run(String[] args)
            throws GeneralSecurityException, IOException {
        if (args.length > 4) {
            throw new IllegalArgumentException(
                    "expected [host] [port] [certificate-directory] [payload]");
        }

        String host = args.length >= 1 ? args[0] : "localhost";
        if (host.isBlank()) {
            throw new IllegalArgumentException("host must not be blank");
        }

        int port = args.length >= 2
                ? TlsSupport.parsePort(args[1])
                : TlsSupport.DEFAULT_PORT;
        Path certificateDirectory = args.length >= 3
                ? Path.of(args[2])
                : TlsSupport.defaultCertificateDirectory();
        String payload = args.length >= 4 ? args[3] : "hello";

        char[] password = TlsSupport.passwordFromEnvironment();
        SSLContext context;
        try {
            context = TlsSupport.createClientContext(certificateDirectory, password);
        } finally {
            Arrays.fill(password, '\0');
        }

        try (Socket transport = new Socket()) {
            transport.connect(
                    new InetSocketAddress(host, port),
                    TlsSupport.CONNECT_TIMEOUT_MILLIS);

            try (SSLSocket socket = (SSLSocket) context.getSocketFactory()
                    .createSocket(transport, host, port, true)) {
                TlsSupport.configureClientSocket(socket);
                socket.setSoTimeout(TlsSupport.READ_TIMEOUT_MILLIS);
                socket.startHandshake();

                MessageCodec.write(
                        socket.getOutputStream(),
                        new ProtocolMessage.Ping(
                                1,
                                payload.getBytes(StandardCharsets.UTF_8)));

                ProtocolMessage response = MessageCodec.read(socket.getInputStream());
                if (response == null) {
                    throw new IOException("server closed the connection without a response");
                }
                printResponse(response);
            }
        }
    }

    private static void printResponse(ProtocolMessage response) throws IOException {
        if (response instanceof ProtocolMessage.Pong pong && pong.requestId() == 1) {
            String payload = new String(pong.payload(), StandardCharsets.UTF_8);
            System.out.printf("PONG requestId=%d payload=%s%n", pong.requestId(), payload);
            return;
        }
        if (response instanceof ProtocolMessage.Error error) {
            throw new IOException("server rejected request: " + error.code());
        }
        throw new IOException("unexpected response message");
    }

    private static void printUsage() {
        System.err.println(
                "Usage: TlsClient [host] [port] [certificate-directory] [payload]");
    }
}
