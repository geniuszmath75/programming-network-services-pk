package org.example.project1.common;

import java.net.DatagramSocket;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.net.UnknownHostException;

/**
 * Helpers so TCP/UDP demos work the same for IPv4 and IPv6.
 */
public final class Net {

    private Net() {
    }

    /**
     * Returns a short label for logs: {@code IPv4} or {@code IPv6}.
     *
     * @param address peer or local bind address
     */
    public static String familyLabel(InetAddress address) {
        return address instanceof Inet6Address ? "IPv6" : "IPv4";
    }

    /**
     * Creates a UDP socket that can talk to {@code peer}.
     * Plain {@code new DatagramSocket()} is often IPv4-only; for an IPv6 peer
     * this binds an IPv6 wildcard ({@code ::}) with an ephemeral port.
     *
     * @param peer destination host (decides address family)
     */
    public static DatagramSocket newDatagramSocket(InetAddress peer) throws SocketException {
        if (peer instanceof Inet6Address) {
            try {
                InetAddress any6 = InetAddress.getByName("::"); // IPv6 any-local
                return new DatagramSocket(new InetSocketAddress(any6, 0)); // ephemeral port
            } catch (UnknownHostException e) {
                throw new SocketException("IPv6 not available: " + e.getMessage());
            }
        }
        return new DatagramSocket();
    }
}
