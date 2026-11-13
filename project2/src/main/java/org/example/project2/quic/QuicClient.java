package org.example.project2.quic;

import org.example.project2.cli.CommandLineSupport;
import org.example.project2.protocol.ProtocolSupport;
import org.example.project2.tls.TlsSupport;
import tech.kwik.core.QuicClientConnection;
import tech.kwik.core.QuicConnection;
import tech.kwik.core.QuicStream;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.time.Duration;

/**
 * QUIC v1 client for the {@code pus-demo/1} application protocol.
 */
public final class QuicClient {

    private QuicClient() {
    }

    public static void main(String[] args) {
        try {
            run(args);
        } catch (IllegalArgumentException exception) {
            System.err.println("Invalid arguments: " + exception.getMessage());
            printUsage();
            System.exit(2);
        } catch (IllegalStateException exception) {
            System.err.println("QUIC configuration error: " + exception.getMessage());
            System.exit(2);
        } catch (GeneralSecurityException | IOException exception) {
            System.err.println("QUIC client failed: " + exception.getMessage());
            System.exit(1);
        }
    }

    private static void run(String[] args)
            throws GeneralSecurityException, IOException {
        CommandLineSupport.ClientOptions options =
                CommandLineSupport.parseClient(args, QuicServer.DEFAULT_PORT);

        KeyStore trustStore = TlsSupport.loadKeyStore(
                options.certificateDirectory().resolve("client-truststore.p12"));

        QuicClientConnection connection = QuicClientConnection.newBuilder()
                .host(options.host())
                .port(options.port())
                .applicationProtocol(QuicServer.APPLICATION_PROTOCOL)
                .version(QuicConnection.QuicVersion.V1)
                .connectTimeout(Duration.ofSeconds(5))
                .maxIdleTimeout(Duration.ofSeconds(30))
                .customTrustStore(trustStore)
                .build();

        try {
            connection.connect();
            QuicStream stream = connection.createStream(true);
            ProtocolSupport.requestAndPrint(
                    stream.getInputStream(),
                    stream.getOutputStream(),
                    options.payload());
            stream.getOutputStream().close();
        } finally {
            connection.closeAndWait(Duration.ofSeconds(2));
        }
    }

    private static void printUsage() {
        System.err.println(
                "Usage: QuicClient [host] [port] [certificate-directory] [payload]");
    }
}
