package org.example.project2.tls;

import org.example.project2.cli.CommandLineSupport;
import org.example.project2.protocol.ProtocolSupport;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLSocket;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.security.GeneralSecurityException;

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
        CommandLineSupport.ClientOptions options =
                CommandLineSupport.parseClient(args, TlsSupport.DEFAULT_PORT);

        SSLContext context = TlsSupport.createClientContext(options.certificateDirectory());

        try (Socket transport = new Socket()) {
            transport.connect(
                    new InetSocketAddress(options.host(), options.port()),
                    TlsSupport.CONNECT_TIMEOUT_MILLIS);

            try (SSLSocket socket = (SSLSocket) context.getSocketFactory()
                    .createSocket(transport, options.host(), options.port(), true)) {
                TlsSupport.configureClientSocket(socket);
                socket.setSoTimeout(TlsSupport.READ_TIMEOUT_MILLIS);
                socket.startHandshake();

                ProtocolSupport.requestAndPrint(
                        socket.getInputStream(),
                        socket.getOutputStream(),
                        options.payload());
            }
        }
    }

    private static void printUsage() {
        System.err.println(
                "Usage: TlsClient [host] [port] [certificate-directory] [payload]");
    }
}
