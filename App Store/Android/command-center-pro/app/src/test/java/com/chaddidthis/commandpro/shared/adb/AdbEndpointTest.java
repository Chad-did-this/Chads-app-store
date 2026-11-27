package com.chaddidthis.commandpro.shared.adb;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import java.io.IOException;
import org.junit.Test;

public class AdbEndpointTest {
    @Test public void acceptsTheWholeAndroidAddress() {
        AdbEndpoint endpoint = AdbEndpoint.parse(" 192.168.1.42:37123 ");
        assertEquals("192.168.1.42", endpoint.host);
        assertEquals(37123, endpoint.port);
    }

    @Test public void stillAcceptsJustAPort() {
        AdbEndpoint endpoint = AdbEndpoint.parse(" 5555 ");
        assertEquals("127.0.0.1", endpoint.host);
        assertEquals(5555, endpoint.port);
    }

    @Test public void acceptsPortLimits() {
        assertEquals(1, AdbEndpoint.parse("127.0.0.1:1").port);
        assertEquals(65535, AdbEndpoint.parse("65535").port);
    }

    @Test public void acceptsBracketedIpv6() {
        AdbEndpoint endpoint = AdbEndpoint.parse("[::1]:37123");
        assertEquals("::1", endpoint.host);
        assertEquals(37123, endpoint.port);
        assertEquals("fe80::1%wlan0", AdbEndpoint.parse("[fe80::1%wlan0]:40567").host);
    }

    @Test public void rejectsInvalidPortsAndMissingParts() {
        String[] invalid = {"", "0", "65536", "9999999999", "-1", "+1", "5.5", "123 45",
            "192.168.1.42", "192.168.1.42:", ":5555", "127.0.0.1:0", "127.0.0.1:65536", "[::1]"};
        for (String value : invalid) {
            assertThrows(value, IllegalArgumentException.class, () -> AdbEndpoint.parse(value));
        }
        assertThrows(IllegalArgumentException.class, () -> AdbEndpoint.parse(null));
    }

    @Test public void rejectsHostnamesUrlsAndMalformedAddresses() {
        String[] invalid = {"example.com:5555", "https://127.0.0.1:5555", "256.1.1.1:5555",
            "1.2.3:5555", "1.2.3.4.5:5555", "::1:5555", "[bad]:5555", "[:::]:5555",
            "[::1]:5555/path", "[::1%]:5555", "[::1%wlan0%bad]:5555", "127.0.0.1:5555:5556"};
        for (String value : invalid) {
            assertThrows(value, IllegalArgumentException.class, () -> AdbEndpoint.parse(value));
        }
    }

    @Test public void acceptsLoopbackConnectionsToThisPhone() throws IOException {
        AdbEndpoint.parse("127.0.0.1:5555").requireThisPhone();
        AdbEndpoint.parse("[::1]:5555").requireThisPhone();
    }

    @Test public void rejectsAddressesThatAreNotAssignedLocally() {
        assertThrows(IOException.class, () -> AdbEndpoint.parse("192.0.2.42:5555").requireThisPhone());
    }
}
