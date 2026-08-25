package yh.hotplugin.system.security;

import org.bouncycastle.crypto.PBEParametersGenerator;
import org.bouncycastle.crypto.digests.SM3Digest;
import org.bouncycastle.crypto.generators.PKCS5S2ParametersGenerator;
import org.bouncycastle.crypto.params.KeyParameter;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/** Versioned, salted PBKDF2-HMAC-SM3 password storage with one-way BCrypt migration. */
public final class GmPasswordEncoder {
    private static final String PREFIX = "pbkdf2-sm3$v1$";
    private static final int ITERATIONS = 200_000;
    private static final int SALT_BYTES = 16;
    private static final int HASH_BYTES = 32;
    private final SecureRandom random = new SecureRandom();
    private final BCryptPasswordEncoder legacyBcrypt = new BCryptPasswordEncoder();

    public String encode(String raw) {
        if (raw == null || raw.isEmpty()) throw new IllegalArgumentException("password is required");
        byte[] salt = new byte[SALT_BYTES];
        random.nextBytes(salt);
        byte[] hash = derive(raw, salt, ITERATIONS);
        return PREFIX + ITERATIONS + "$" + b64(salt) + "$" + b64(hash);
    }

    public boolean matches(String raw, String stored) {
        if (raw == null || stored == null) return false;
        if (stored.startsWith(PREFIX)) return matchesGm(raw, stored);
        return stored.startsWith("$2") && legacyBcrypt.matches(raw, stored);
    }

    public boolean needsUpgrade(String stored) {
        return stored != null && !stored.startsWith(PREFIX);
    }

    public boolean isGmEncoded(String stored) {
        return stored != null && stored.startsWith(PREFIX);
    }

    private boolean matchesGm(String raw, String stored) {
        try {
            String[] parts = stored.split("\\$", -1);
            if (parts.length != 5 || !"pbkdf2-sm3".equals(parts[0]) || !"v1".equals(parts[1])) return false;
            int iterations = Integer.parseInt(parts[2]);
            if (iterations < 10_000 || iterations > 2_000_000) return false;
            byte[] salt = Base64.getDecoder().decode(parts[3]);
            byte[] expected = Base64.getDecoder().decode(parts[4]);
            if (salt.length < SALT_BYTES || expected.length != HASH_BYTES) return false;
            return MessageDigest.isEqual(expected, derive(raw, salt, iterations));
        } catch (RuntimeException malformed) {
            return false;
        }
    }

    private static byte[] derive(String raw, byte[] salt, int iterations) {
        PKCS5S2ParametersGenerator generator = new PKCS5S2ParametersGenerator(new SM3Digest());
        generator.init(PBEParametersGenerator.PKCS5PasswordToUTF8Bytes(raw.toCharArray()), salt, iterations);
        return ((KeyParameter) generator.generateDerivedParameters(HASH_BYTES * 8)).getKey();
    }

    private static String b64(byte[] value) {
        return Base64.getEncoder().withoutPadding().encodeToString(value);
    }
}
