package org.example.project2.cli;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Parses the common command-line shape used by the TCP and QUIC demos.
 */
public final class CommandLineSupport {

    private CommandLineSupport() {
    }

    public record ClientOptions(
            String host,
            int port,
            Path certificateDirectory,
            String payload) {
    }

    public record ServerOptions(
            int port,
            Path certificateDirectory) {
    }

    public static ClientOptions parseClient(String[] args, int defaultPort) {
        Objects.requireNonNull(args, "args");
        if (args.length > 4) {
            throw new IllegalArgumentException(
                    "expected [host] [port] [certificate-directory] [payload]");
        }

        String host = args.length >= 1 ? args[0] : "localhost";
        if (host.isBlank()) {
            throw new IllegalArgumentException("host must not be blank");
        }

        int port = args.length >= 2 ? parsePort(args[1]) : defaultPort;
        Path certificateDirectory = args.length >= 3
                ? Path.of(args[2])
                : defaultCertificateDirectory();
        String payload = args.length >= 4 ? args[3] : "hello";

        return new ClientOptions(host, port, certificateDirectory, payload);
    }

    public static ServerOptions parseServer(String[] args, int defaultPort) {
        Objects.requireNonNull(args, "args");
        if (args.length > 2) {
            throw new IllegalArgumentException("expected [port] [certificate-directory]");
        }

        int port = args.length >= 1 ? parsePort(args[0]) : defaultPort;
        Path certificateDirectory = args.length == 2
                ? Path.of(args[1])
                : defaultCertificateDirectory();

        return new ServerOptions(port, certificateDirectory);
    }

    private static int parsePort(String value) {
        try {
            int port = Integer.parseInt(value);
            if (port < 1 || port > 65_535) {
                throw new NumberFormatException();
            }
            return port;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("port must be between 1 and 65535");
        }
    }

    private static Path defaultCertificateDirectory() {
        return Path.of("project2", ".certs");
    }
}
