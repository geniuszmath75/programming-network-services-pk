package org.example.project2.tls;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManagerFactory;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.util.Objects;

/**
 * Creates the JSSE contexts used by the mTLS TCP demo.
 */
public final class TlsSupport {

    public static final String TLS_VERSION = "TLSv1.3";
    public static final int CONNECT_TIMEOUT_MILLIS = 5_000;
    public static final int READ_TIMEOUT_MILLIS = 10_000;
    public static final int DEFAULT_PORT = 9_443;

    private static final String KEYSTORE_TYPE = "PKCS12";
    private static final String PASSWORD_ENVIRONMENT_VARIABLE = "PUS_CERT_PASSWORD";

    private TlsSupport() {
    }

    public static SSLContext createServerContext(Path certificateDirectory, char[] password)
            throws GeneralSecurityException, IOException {
        Objects.requireNonNull(certificateDirectory, "certificateDirectory");
        return createContext(
                certificateDirectory.resolve("server-keystore.p12"),
                certificateDirectory.resolve("server-truststore.p12"),
                password);
    }

    public static SSLContext createClientContext(Path certificateDirectory, char[] password)
            throws GeneralSecurityException, IOException {
        Objects.requireNonNull(certificateDirectory, "certificateDirectory");
        return createContext(
                certificateDirectory.resolve("client-keystore.p12"),
                certificateDirectory.resolve("client-truststore.p12"),
                password);
    }

    public static char[] passwordFromEnvironment() {
        String password = System.getenv(PASSWORD_ENVIRONMENT_VARIABLE);
        if (password == null || password.length() < 6) {
            throw new IllegalStateException(
                    "set PUS_CERT_PASSWORD to the password used by the generated PKCS12 stores");
        }
        return password.toCharArray();
    }

    public static Path defaultCertificateDirectory() {
        return Path.of("project2", ".certs");
    }

    public static int parsePort(String value) {
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

    public static void configureServerSocket(SSLServerSocket serverSocket) {
        serverSocket.setEnabledProtocols(new String[]{TLS_VERSION});
        serverSocket.setNeedClientAuth(true);
    }

    public static void configureClientSocket(SSLSocket socket) {
        socket.setEnabledProtocols(new String[]{TLS_VERSION});
        SSLParameters parameters = socket.getSSLParameters();
        parameters.setEndpointIdentificationAlgorithm("HTTPS");
        socket.setSSLParameters(parameters);
    }

    private static SSLContext createContext(
            Path keyStorePath,
            Path trustStorePath,
            char[] password)
            throws GeneralSecurityException, IOException {
        Objects.requireNonNull(password, "password");

        KeyStore keyStore = loadKeyStore(keyStorePath, password);
        KeyManagerFactory keyManagerFactory =
                KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        keyManagerFactory.init(keyStore, password);

        KeyStore trustStore = loadKeyStore(trustStorePath, password);
        TrustManagerFactory trustManagerFactory =
                TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trustManagerFactory.init(trustStore);

        SSLContext context = SSLContext.getInstance(TLS_VERSION);
        context.init(
                keyManagerFactory.getKeyManagers(),
                trustManagerFactory.getTrustManagers(),
                null);
        return context;
    }

    private static KeyStore loadKeyStore(Path path, char[] password)
            throws GeneralSecurityException, IOException {
        KeyStore keyStore = KeyStore.getInstance(KEYSTORE_TYPE);
        try (InputStream input = Files.newInputStream(path)) {
            keyStore.load(input, password);
        }
        return keyStore;
    }
}
