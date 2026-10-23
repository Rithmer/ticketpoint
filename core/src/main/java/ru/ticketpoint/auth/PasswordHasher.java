package ru.ticketpoint.auth;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public final class PasswordHasher {
    public static void validate(String password) {
        if (password == null || password.length() < 8 || password.length() > 128 || password.isBlank()) {
            throw new IllegalArgumentException("Пароль: от 8 до 128 символов; не используйте только пробелы");
        }
    }

    public String hash(String password) {
        validate(password);
        if (password.getBytes(StandardCharsets.UTF_8).length > 72)
            throw new IllegalArgumentException("Пароль слишком длинный. Используйте меньше символов.");
        return new BCryptPasswordEncoder(12).encode(password);
    }

    public boolean matches(String password, String encoded) {
        if (password == null || password.length() > 128) return false;
        if (encoded == null) return false;
        if (encoded.startsWith("$2")) return password.getBytes(StandardCharsets.UTF_8).length <= 72
                && new BCryptPasswordEncoder().matches(password, encoded);
        try {
            String[] parts = encoded.split("\\$");
            if (parts.length != 4 || !parts[0].equals("pbkdf2-sha256")) return false;
            int iterations = Integer.parseInt(parts[1]);
            if (iterations < 100_000 || iterations > 2_000_000) return false;
            return MessageDigest.isEqual(Base64.getDecoder().decode(parts[3]),
                    derive(password, Base64.getDecoder().decode(parts[2]), iterations));
        } catch (IllegalArgumentException invalid) {
            return false;
        }
    }

    private static byte[] derive(String password, byte[] salt, int iterations) {
        char[] chars = password.toCharArray();
        PBEKeySpec spec = new PBEKeySpec(chars, salt, iterations, 256);
        Arrays.fill(chars, '\0');
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException error) {
            throw new IllegalStateException("Не удалось защитить пароль", error);
        } finally {
            spec.clearPassword();
        }
    }
}
