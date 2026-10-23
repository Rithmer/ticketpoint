package ru.ticketpoint.backend.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;

import java.time.Instant;
import java.util.*;
import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import ru.ticketpoint.backend.domain.UserAccount;

@Service
public class JwtService {
    private final SecretKey key;
    private final String issuer;
    private final int minutes;

    public JwtService(
            @Value("${app.jwt.secret-base64}") String secret,
            @Value("${app.jwt.issuer}") String issuer,
            @Value("${app.jwt.access-minutes}") int minutes) {
        this.key = Keys.hmacShaKeyFor(Base64.getDecoder().decode(secret));
        this.issuer = issuer;
        this.minutes = minutes;
        if (minutes < 1 || minutes > 60) throw new IllegalArgumentException("access-minutes: 1..60");
    }

    public Instant expiry() {
        return Instant.now().plusSeconds(minutes * 60L);
    }

    public String issue(UserAccount user, Instant expiry) {
        return Jwts.builder()
                .issuer(issuer)
                .subject(user.getPublicId().toString())
                .claim("version", user.getVersion())
                .issuedAt(new Date())
                .expiration(Date.from(expiry))
                .signWith(key)
                .compact();
    }

    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .requireIssuer(issuer)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
