package ru.ticketpoint.auth;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PasswordHasherTest {
    @Test
    void saltsPasswordsAndRejectsWrongAndMalformedHashes() {
        PasswordHasher hasher = new PasswordHasher();
        String password = "A secure password 2026!";
        String first = hasher.hash(password), second = hasher.hash(password);
        assertNotEquals(first, second);
        assertTrue(first.startsWith("$2"));
        assertThrows(IllegalArgumentException.class, () -> hasher.hash("я".repeat(37)));
        assertFalse(first.contains(password));
        assertTrue(hasher.matches(password, first));
        assertFalse(hasher.matches("wrong password", first));
        assertFalse(hasher.matches(password, "pbkdf2-sha256$999999999$bad$bad"));
        assertFalse(hasher.matches(password, "not a hash"));
        assertFalse(hasher.matches(null, first));
        assertTrue(hasher.matches("Legacy-password-2026!", "pbkdf2-sha256$600000$bGVnYWN5LXNhbHQtMjAyNg==$et88PYX5y+hCI1dVvQA12vOpEJAiGMeY2JD24a7wH/k="));
    }

    @Test
    void validatesPasswordsAndLoginWithoutEmail() {
        assertThrows(IllegalArgumentException.class, () -> PasswordHasher.validate("short"));
        assertThrows(IllegalArgumentException.class, () -> PasswordHasher.validate(" ".repeat(12)));
        assertThrows(IllegalArgumentException.class, () -> PasswordHasher.validate("a".repeat(129)));
        for (String password : new String[]{"user12345", "manager12345", "admin12345"}) {
            PasswordHasher hasher = new PasswordHasher();
            assertTrue(hasher.matches(password, hasher.hash(password)));
        }
        assertEquals("team.manager", AuthService.login(" Team.Manager "));
        assertThrows(IllegalArgumentException.class, () -> AuthService.login("admin@example.org"));
        assertThrows(IllegalArgumentException.class, () -> AuthService.login("ab"));
        assertFalse(new AuthConfig("admin", "Admin", "private-password").toString().contains("private-password"));
    }
}
