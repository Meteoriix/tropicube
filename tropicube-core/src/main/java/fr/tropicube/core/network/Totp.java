package fr.tropicube.core.network;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Locale;

/** RFC 6238 six-digit TOTP with a 30-second period. */
public final class Totp {
    private static final char[] BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567".toCharArray();

    private Totp() {}

    public static String newSecret() {
        byte[] bytes = new byte[20];
        new SecureRandom().nextBytes(bytes);
        return encodeBase32(bytes);
    }

    public static boolean verify(String secret, String code, long epochSeconds, long lastUsedStep) {
        if (code == null || !code.matches("\\d{6}")) return false;
        long current = epochSeconds / 30;
        for (long step = current - 1; step <= current + 1; step++) {
            if (step > lastUsedStep && generate(secret, step).equals(code)) return true;
        }
        return false;
    }

    public static long acceptedStep(String secret, String code, long epochSeconds, long lastUsedStep) {
        if (code == null || !code.matches("\\d{6}")) return -1;
        long current = epochSeconds / 30;
        for (long step = current - 1; step <= current + 1; step++) {
            if (step > lastUsedStep && generate(secret, step).equals(code)) return step;
        }
        return -1;
    }

    static String generate(String secret, long step) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(decodeBase32(secret), "HmacSHA1"));
            byte[] digest = mac.doFinal(ByteBuffer.allocate(Long.BYTES).putLong(step).array());
            int offset = digest[digest.length - 1] & 0x0f;
            int binary = ((digest[offset] & 0x7f) << 24) | ((digest[offset + 1] & 0xff) << 16)
                    | ((digest[offset + 2] & 0xff) << 8) | (digest[offset + 3] & 0xff);
            return "%06d".formatted(binary % 1_000_000);
        } catch (GeneralSecurityException error) {
            throw new IllegalStateException("TOTP indisponible", error);
        }
    }

    private static String encodeBase32(byte[] source) {
        StringBuilder result = new StringBuilder((source.length * 8 + 4) / 5);
        int buffer = 0, bits = 0;
        for (byte value : source) {
            buffer = (buffer << 8) | (value & 0xff);
            bits += 8;
            while (bits >= 5) {
                bits -= 5;
                result.append(BASE32[(buffer >>> bits) & 31]);
            }
        }
        if (bits > 0) result.append(BASE32[(buffer << (5 - bits)) & 31]);
        return result.toString();
    }

    private static byte[] decodeBase32(String value) {
        String normalized = value.replace("=", "").replace(" ", "").toUpperCase(Locale.ROOT);
        byte[] result = new byte[normalized.length() * 5 / 8];
        int buffer = 0, bits = 0, index = 0;
        for (char character : normalized.toCharArray()) {
            int decoded = character >= 'A' && character <= 'Z' ? character - 'A'
                    : character >= '2' && character <= '7' ? character - '2' + 26 : -1;
            if (decoded < 0) throw new IllegalArgumentException("Secret Base32 invalide");
            buffer = (buffer << 5) | decoded;
            bits += 5;
            if (bits >= 8) {
                bits -= 8;
                if (index < result.length) result[index++] = (byte) (buffer >>> bits);
            }
        }
        return result;
    }
}
