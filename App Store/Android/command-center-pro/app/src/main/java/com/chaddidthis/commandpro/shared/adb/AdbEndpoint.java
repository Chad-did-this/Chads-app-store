package com.chaddidthis.commandpro.shared.adb;

import java.io.IOException;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.UnknownHostException;

final class AdbEndpoint {
    final String host;
    final int port;

    private AdbEndpoint(String host, int port) {
        this.host = host;
        this.port = port;
    }

    static AdbEndpoint parse(String text) {
        String value = text == null ? "" : text.trim();
        String host = "127.0.0.1";
        String number = value;

        if (value.startsWith("[")) {
            int end = value.indexOf(']');
            if (end < 0 || end + 1 >= value.length() || value.charAt(end + 1) != ':') {
                throw new IllegalArgumentException("Use [IPv6 address]:port");
            }
            host = value.substring(1, end);
            if (!ipv6(host)) throw new IllegalArgumentException("Enter a valid IPv6 address");
            number = value.substring(end + 2);
        } else if (value.indexOf(':') >= 0) {
            int separator = value.indexOf(':');
            host = value.substring(0, separator);
            if (!ipv4(host)) throw new IllegalArgumentException("Use IP address:port, or [IPv6 address]:port");
            number = value.substring(separator + 1);
        }

        if (!number.matches("[0-9]{1,5}")) {
            throw new IllegalArgumentException("Enter the IP address:port shown by Android, or just the port");
        }
        int port = Integer.parseInt(number);
        if (port < 1 || port > 65535) throw new IllegalArgumentException("Port must be 1–65535");
        return new AdbEndpoint(host, port);
    }

    void requireThisPhone() throws IOException {
        InetAddress address = InetAddress.getByName(host);
        if (!address.isLoopbackAddress() && NetworkInterface.getByInetAddress(address) == null) {
            throw new IOException("Use the IP address and port shown on this phone's Wireless debugging screen");
        }
    }

    private static boolean ipv4(String host) {
        String[] parts = host.split("\\.", -1);
        if (parts.length != 4) return false;
        for (String part : parts) {
            if (!part.matches("[0-9]{1,3}") || Integer.parseInt(part) > 255) return false;
        }
        return true;
    }

    private static boolean ipv6(String host) {
        String address = host;
        int zone = host.indexOf('%');
        if (zone >= 0) {
            if (!host.substring(zone + 1).matches("[a-zA-Z0-9_.-]+")) return false;
            address = host.substring(0, zone);
        }
        if (!address.contains(":") || !address.matches("[a-fA-F0-9:.]+")) return false;
        try {
            InetAddress.getByName(address);
            return true;
        } catch (UnknownHostException invalid) {
            return false;
        }
    }
}
