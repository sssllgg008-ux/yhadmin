package yh.hotplugin.system.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;

class GmPasswordEncoderTest {
    private final GmPasswordEncoder encoder = new GmPasswordEncoder();

    @Test void encodesWithSaltedVersionedSm3AndVerifies() {
        String first = encoder.encode("StrongPassword-123!");
        String second = encoder.encode("StrongPassword-123!");
        assertTrue(first.startsWith("pbkdf2-sm3$v1$200000$"));
        assertNotEquals(first, second);
        assertTrue(encoder.matches("StrongPassword-123!", first));
        assertFalse(encoder.matches("wrong", first));
        assertFalse(encoder.needsUpgrade(first));
    }

    @Test void acceptsLegacyBcryptOnlyForOneWayMigration() {
        String legacy = new BCryptPasswordEncoder().encode("LegacyPassword-123!");
        assertTrue(encoder.matches("LegacyPassword-123!", legacy));
        assertTrue(encoder.needsUpgrade(legacy));
        assertFalse(encoder.matches("wrong", legacy));
    }

    @Test void rejectsPlaintextAndMalformedHashes() {
        assertFalse(encoder.matches("password", "password"));
        assertFalse(encoder.matches("password", "pbkdf2-sm3$v1$bad$value"));
        assertFalse(encoder.matches(null, null));
    }
}
