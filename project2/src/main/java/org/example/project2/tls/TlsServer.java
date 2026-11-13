package org.example.project2.tls;

import org.example.project2.cli.CommandLineSupport;
import org.example.project2.protocol.ProtocolSupport;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSocket;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.SocketTimeoutException;
import java.security.GeneralSecurityException;

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
        CommandLineSupport.ServerOptions options =
                CommandLineSupport.parseServer(args, TlsSupport.DEFAULT_PORT);

        SSLContext context = TlsSupport.createServerContext(options.certificateDirectory());

        try (SSLServerSocket serverSocket =
                     (SSLServerSocket) context.getServerSocketFactory()
                             .createServerSocket(options.port())) {
            TlsSupport.configureServerSocket(serverSocket);
            System.out.printf(
                    "TLS 1.3 server listening on port %d; client certificate required%n",
                    options.port());

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

        while (ProtocolSupport.handleRequest(input, output)) {
            // Keep serving requests on this TCP connection.
        }
    }

    private static void printUsage() {
        System.err.println("Usage: TlsServer [port] [certificate-directory]");
    }
}
