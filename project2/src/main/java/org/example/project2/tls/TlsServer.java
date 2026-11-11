package org.example.project2.tls;

import org.example.project2.protocol.MessageCodec;
import org.example.project2.protocol.ProtocolException;
import org.example.project2.protocol.ProtocolMessage;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSocket;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.SocketTimeoutException;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.util.Arrays;

/**
 * TLS 1.3 TCP server with mandatory client-certificate authentication.
 */
public final class TlsServer {

    private TlsServer() {
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
        } catch (GeneralSecurityException | IOException exception) {
            System.err.println("TLS server failed: " + exception.getClass().getSimpleName() + exception.getMessage());
            System.exit(1);
        }
    }

    private static void run(String[] args)
            throws GeneralSecurityException, IOException {
        if (args.length > 2) {
            throw new IllegalArgumentException("expected [port] [certificate-directory]");
        }

        int port = args.length >= 1
                ? TlsSupport.parsePort(args[0])
                : TlsSupport.DEFAULT_PORT;
        Path certificateDirectory = args.length == 2
                ? Path.of(args[1])
                : TlsSupport.defaultCertificateDirectory();

        char[] password = TlsSupport.passwordFromEnvironment();
        SSLContext context;
        try {
            context = TlsSupport.createServerContext(certificateDirectory, password);
        } finally {
            Arrays.fill(password, '\0');
        }

        try (SSLServerSocket serverSocket =
                     (SSLServerSocket) context.getServerSocketFactory().createServerSocket(port)) {
            TlsSupport.configureServerSocket(serverSocket);
            System.out.printf(
                    "TLS 1.3 server listening on port %d; client certificate required%n",
                    port);

            while (!serverSocket.isClosed()) {
                try (SSLSocket socket = (SSLSocket) serverSocket.accept()) {
                    handleConnection(socket);
                } catch (SSLHandshakeException exception) {
                    System.err.println("TLS handshake rejected");
                } catch (SocketTimeoutException exception) {
                    System.err.println("TLS connection timed out");
                } catch (IOException exception) {
                    System.err.println("TLS connection closed unexpectedly");
                }
            }
        }
    }

    private static void handleConnection(SSLSocket socket) throws IOException {
        socket.setSoTimeout(TlsSupport.READ_TIMEOUT_MILLIS);
        socket.startHandshake();

        InputStream input = socket.getInputStream();
        OutputStream output = socket.getOutputStream();

        while (true) {
            ProtocolMessage message;
            try {
                message = MessageCodec.read(input);
            } catch (ProtocolException exception) {
                MessageCodec.write(
                        output,
                        new ProtocolMessage.Error(
                                exception.code().name(),
                                "invalid protocol frame"));
                return;
            }

            if (message == null) {
                return;
            }
            if (message instanceof ProtocolMessage.Ping ping) {
                MessageCodec.write(
                        output,
                        new ProtocolMessage.Pong(ping.requestId(), ping.payload()));
            } else {
                MessageCodec.write(
                        output,
                        new ProtocolMessage.Error(
                                "INVALID_MESSAGE_TYPE",
                                "expected PING"));
            }
        }
    }

    private static void printUsage() {
        System.err.println("Usage: TlsServer [port] [certificate-directory]");
    }
}
