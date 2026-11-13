package org.example.project2.quic;

import org.example.project2.cli.CommandLineSupport;
import org.example.project2.protocol.ProtocolSupport;
import org.example.project2.tls.TlsSupport;
import tech.kwik.core.QuicConnection;
import tech.kwik.core.QuicStream;
import tech.kwik.core.log.NullLogger;
import tech.kwik.core.server.ApplicationProtocolConnection;
import tech.kwik.core.server.ApplicationProtocolConnectionFactory;
import tech.kwik.core.server.ServerConnectionConfig;
import tech.kwik.core.server.ServerConnector;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.util.concurrent.CountDownLatch;

/**
 * QUIC v1 server for the {@code pus-demo/1} application protocol.
 */
public final class QuicServer {

    public static final String APPLICATION_PROTOCOL = "pus-demo/1";
    public static final int DEFAULT_PORT = 9_444;

    private static final int MAX_CONCURRENT_STREAMS = 10;
    private static final long STREAM_ERROR_CODE = 1;

    private QuicServer() {
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
            System.err.println("QUIC server failed: " + exception.getClass().getSimpleName());
            System.exit(1);
        }
    }

    private static void run(String[] args)
            throws GeneralSecurityException, IOException {
        CommandLineSupport.ServerOptions options =
                CommandLineSupport.parseServer(args, DEFAULT_PORT);

        ServerConnector connector = TlsSupport.withPassword(password -> {
            KeyStore keyStore = TlsSupport.loadKeyStore(
                    options.certificateDirectory().resolve("server-keystore.p12"),
                    password);

            ServerConnectionConfig configuration = ServerConnectionConfig.builder()
                    .maxIdleTimeoutInSeconds(30)
                    .maxOpenPeerInitiatedUnidirectionalStreams(0)
                    .maxOpenPeerInitiatedBidirectionalStreams(MAX_CONCURRENT_STREAMS)
                    .retryRequired(true)
                    .build();

            return ServerConnector.builder()
                    .withPort(options.port())
                    .withKeyStore(keyStore, "pus-server", password)
                    .withSupportedVersion(QuicConnection.QuicVersion.V1)
                    .withConfiguration(configuration)
                    .withLogger(new NullLogger())
                    .build();
        });

        connector.registerApplicationProtocol(
                APPLICATION_PROTOCOL,
                new ApplicationProtocolConnectionFactory() {
                    @Override
                    public int maxConcurrentPeerInitiatedUnidirectionalStreams() {
                        return 0;
                    }

                    @Override
                    public int maxConcurrentPeerInitiatedBidirectionalStreams() {
                        return MAX_CONCURRENT_STREAMS;
                    }

                    @Override
                    public ApplicationProtocolConnection createConnection(
                            String protocol,
                            QuicConnection connection) {
                        return new ApplicationProtocolConnection() {
                            @Override
                            public void acceptPeerInitiatedStream(QuicStream stream) {
                                if (!stream.isBidirectional()) {
                                    stream.abortReading(STREAM_ERROR_CODE);
                                    return;
                                }
                                Thread.startVirtualThread(() -> handleStream(stream));
                            }
                        };
                    }
                });

        try (connector) {
            connector.start();
            System.out.printf(
                    "QUIC v1 server listening on UDP port %d; ALPN=%s%n",
                    options.port(),
                    APPLICATION_PROTOCOL);

            Runtime.getRuntime().addShutdownHook(
                    new Thread(connector::close, "quic-server-shutdown"));
            new CountDownLatch(1).await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private static void handleStream(QuicStream stream) {
        try {
            InputStream input = stream.getInputStream();
            OutputStream output = stream.getOutputStream();
            ProtocolSupport.handleRequest(input, output);
            output.close();
        } catch (IOException | RuntimeException exception) {
            try {
                stream.resetStream(STREAM_ERROR_CODE);
            } catch (RuntimeException ignored) {
                // The peer may already have closed the stream.
            }
            System.err.println(
                    "QUIC stream failed: " + exception.getClass().getSimpleName());
        }
    }

    private static void printUsage() {
        System.err.println("Usage: QuicServer [port] [certificate-directory]");
    }
}
