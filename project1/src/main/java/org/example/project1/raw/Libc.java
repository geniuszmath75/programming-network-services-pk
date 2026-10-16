package org.example.project1.raw;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;

/**
 * Panama FFM bindings to Linux libc used by {@link RawSocket}.
 * No JNI and no hand-written C — only downcalls to {@code socket}, {@code sendto}, etc.
 */
final class Libc {

    static final int AF_INET = 2;                 // IPv4 domain
    static final int SOCK_RAW = 3;                // raw socket type
    static final int IPPROTO_EXPERIMENTAL = 253;  // custom L4-ish proto
    static final int SOL_SOCKET = 1;              // socket option level
    static final int SO_RCVTIMEO = 20;            // receive timeout option
    static final int SOCKADDR_IN_SIZE = 16;       // sizeof(sockaddr_in)

    private static final Linker LINKER = Linker.nativeLinker();
    private static final SymbolLookup LIBC = LINKER.defaultLookup();

    static final MethodHandle SOCKET = downcall(
            "socket",
            FunctionDescriptor.of(
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT));

    static final MethodHandle CLOSE = downcall(
            "close",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));

    static final MethodHandle SENDTO = downcall(
            "sendto",
            FunctionDescriptor.of(
                    ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS,
                    ValueLayout.JAVA_INT));

    static final MethodHandle RECVFROM = downcall(
            "recvfrom",
            FunctionDescriptor.of(
                    ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS));

    static final MethodHandle SETSOCKOPT = downcall(
            "setsockopt",
            FunctionDescriptor.of(
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS,
                    ValueLayout.JAVA_INT));

    private Libc() {
    }

    /**
     * Resolves a libc symbol and builds an FFM downcall handle for it.
     *
     * @param name       C function name (e.g. {@code sendto})
     * @param descriptor native argument/return layout
     */
    private static MethodHandle downcall(String name, FunctionDescriptor descriptor) {
        MemorySegment symbol = LIBC.find(name)
                .orElseThrow(() -> new UnsatisfiedLinkError("libc symbol not found: " + name));
        return LINKER.downcallHandle(symbol, descriptor);
    }

    /**
     * Creates a socket descriptor ({@code socket(2)}).
     *
     * @param domain   address family ({@link #AF_INET})
     * @param type     socket type ({@link #SOCK_RAW})
     * @param protocol IP protocol number ({@link #IPPROTO_EXPERIMENTAL})
     * @return file descriptor, or negative on failure
     */
    static int socket(int domain, int type, int protocol) {
        try {
            return (int) SOCKET.invokeExact(domain, type, protocol);
        } catch (Throwable t) {
            throw new NativeCallException("socket", t);
        }
    }

    /**
     * Closes a socket descriptor ({@code close(2)}).
     *
     * @param fd socket descriptor from {@link #socket}
     */
    static int close(int fd) {
        try {
            return (int) CLOSE.invokeExact(fd);
        } catch (Throwable t) {
            throw new NativeCallException("close", t);
        }
    }

    /**
     * Sends bytes to a destination address ({@code sendto(2)}).
     *
     * @param fd       socket descriptor
     * @param buf      payload bytes in native memory
     * @param len      number of bytes to send
     * @param flags    send flags (0 = none)
     * @param destAddr {@code sockaddr_in} destination
     * @param addrLen  size of {@code destAddr}
     * @return bytes sent, or negative on error
     */
    static long sendto(
            int fd,
            MemorySegment buf,
            long len,
            int flags,
            MemorySegment destAddr,
            int addrLen) {
        try {
            return (long) SENDTO.invokeExact(fd, buf, len, flags, destAddr, addrLen);
        } catch (Throwable t) {
            throw new NativeCallException("sendto", t);
        }
    }

    /**
     * Receives a packet and optional peer address ({@code recvfrom(2)}).
     *
     * @param fd      socket descriptor
     * @param buf     receive buffer in native memory
     * @param len     buffer capacity
     * @param flags   recv flags (0 = none)
     * @param srcAddr out: peer {@code sockaddr_in} (may be unused by caller)
     * @param addrLen in/out: size of {@code srcAddr}
     * @return bytes received, or negative on error/timeout
     */
    static long recvfrom(
            int fd,
            MemorySegment buf,
            long len,
            int flags,
            MemorySegment srcAddr,
            MemorySegment addrLen) {
        try {
            return (long) RECVFROM.invokeExact(fd, buf, len, flags, srcAddr, addrLen);
        } catch (Throwable t) {
            throw new NativeCallException("recvfrom", t);
        }
    }

    /**
     * Sets a socket option ({@code setsockopt(2)}), e.g. receive timeout.
     *
     * @param fd      socket descriptor
     * @param level   option level ({@link #SOL_SOCKET})
     * @param optName option id ({@link #SO_RCVTIMEO})
     * @param optVal  option value bytes
     * @param optLen  size of {@code optVal}
     * @return 0 on success
     */
    static int setsockopt(int fd, int level, int optName, MemorySegment optVal, int optLen) {
        try {
            return (int) SETSOCKOPT.invokeExact(fd, level, optName, optVal, optLen);
        } catch (Throwable t) {
            throw new NativeCallException("setsockopt", t);
        }
    }

    /** Wraps a failed native invoke with the libc call name. */
    static final class NativeCallException extends RuntimeException {
        NativeCallException(String call, Throwable cause) {
            super(call + " failed", cause);
        }
    }
}
