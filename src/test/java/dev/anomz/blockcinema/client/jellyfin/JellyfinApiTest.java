package dev.anomz.blockcinema.client.jellyfin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.Test;

class JellyfinApiTest {
    @Test
    void serverAddressesAreCleanedUp() {
        assertEquals(Optional.of("http://192.168.1.5:8096"), JellyfinApi.normalizeServer(" 192.168.1.5:8096/ "));
        assertEquals(Optional.of("https://jellyfin.example.com/jf"), JellyfinApi.normalizeServer("https://jellyfin.example.com/jf/"));
        assertTrue(JellyfinApi.normalizeServer("").isEmpty());
        assertTrue(JellyfinApi.normalizeServer("ftp://example.com").isEmpty());
        assertTrue(JellyfinApi.normalizeServer("https://user:pw@example.com").isEmpty());
        assertTrue(JellyfinApi.normalizeServer("https://example.com/?x=1").isEmpty());
    }

    @Test
    void authorizationHeaderNamesTheApp() {
        assertEquals("MediaBrowser Client=\"Block Cinema\", Device=\"Minecraft\", DeviceId=\"d1\", Version=\"0.1.0\"",
                new JellyfinApi("http://x", "d1", "0.1.0", null).authorization());
        assertEquals("MediaBrowser Client=\"Block Cinema\", Device=\"Minecraft\", DeviceId=\"d1\", Version=\"0.1.0\", Token=\"t\"",
                new JellyfinApi("http://x", "d1", "0.1.0", "t").authorization());
    }
}
