package org.example.project1.raw;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;

/**
 * Minimal Linux libc bindings via Panama FFM for SOCK_RAW demos.
 */
final class Libc {

    /** Protocol family: 2 - IPv4 */
    static final int AF_INET = 2;
    /** Socket type: SOCK_RAW - raw socket */
    static final int SOCK_RAW = 3;
    /** Experimental / testing IP protocol number (RFC 3692). */
    static final int IPPROTO_EXPERIMENTAL = 253;
    static final int SOL_SOCKET = 1;
    static final int SO_RCVTIMEO = 20;
    static final int SOCKADDR_IN_SIZE = 16;

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

    private static MethodHandle downcall(String name, FunctionDescriptor descriptor) {
        MemorySegment symbol = LIBC.find(name)
                .orElseThrow(() -> new UnsatisfiedLinkError("libc symbol not found: " + name));
        return LINKER.downcallHandle(symbol, descriptor);
    }

    static int socket(int domain, int type, int protocol) {
        try {
            return (int) SOCKET.invokeExact(domain, type, protocol);
        } catch (Throwable t) {
            throw new NativeCallException("socket", t);
        }
    }

    static int close(int fd) {
        try {
            return (int) CLOSE.invokeExact(fd);
        } catch (Throwable t) {
            throw new NativeCallException("close", t);
        }
    }

    static long sendto(
            int fd,
            MemorySegment buf, // buffer from which data is sent
            long len, // bytes send
            int flags, // optional flags
            MemorySegment destAddr, // destination address
            int addrLen) // socket structure size
    {
        try {
            return (long) SENDTO.invokeExact(fd, buf, len, flags, destAddr, addrLen);
        } catch (Throwable t) {
            throw new NativeCallException("sendto", t);
        }
    }

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

    static int setsockopt(int fd, int level, int optName, MemorySegment optVal, int optLen) {
        try {
            return (int) SETSOCKOPT.invokeExact(fd, level, optName, optVal, optLen);
        } catch (Throwable t) {
            throw new NativeCallException("setsockopt", t);
        }
    }

    static final class NativeCallException extends RuntimeException {
        NativeCallException(String call, Throwable cause) {
            super(call + " failed", cause);
        }
    }
}
