package org.example.project1.common;

import java.net.DatagramSocket;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.net.UnknownHostException;

/**
 * Small helpers so the same TCP/UDP entrypoints work for IPv4 and IPv6.
 */
public final class Net {

    private Net() {
    }

    public static String familyLabel(InetAddress address) {
        return address instanceof Inet6Address ? "IPv6" : "IPv4";
    }

    /**
     * Unbound UDP sockets default to IPv4 on many JVMs; bind an IPv6 wildcard
     * when talking to an IPv6 peer so datagrams can actually be sent.
     */
    public static DatagramSocket newDatagramSocket(InetAddress peer) throws SocketException {
        if (peer instanceof Inet6Address) {
            try {
                InetAddress any6 = InetAddress.getByName("::");
                return new DatagramSocket(new InetSocketAddress(any6, 0));
            } catch (UnknownHostException e) {
                throw new SocketException("IPv6 not available: " + e.getMessage());
            }
        }
        return new DatagramSocket();
    }
}
