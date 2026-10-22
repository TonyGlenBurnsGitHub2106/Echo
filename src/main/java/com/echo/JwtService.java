package com.echo;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * Echo's tokens (Story 5653), the same two-token shape as LeonardoWeb so its auth.js works
 * unchanged: a short ACCESS token the page holds in memory and sends as a Bearer header, and a
 * long REFRESH token in an httpOnly cookie that mints new access tokens. The refresh token
 * carries the person's SessionGeneration; Sign out everywhere (or a password change) bumps it,
 * and every older refresh token stops working.
 */
@Service
public class JwtService {

    static final String TYPE_ACCESS = "access", TYPE_REFRESH = "refresh";
    private static final String CLAIM_TYPE = "typ", CLAIM_NAME = "usr", CLAIM_GEN = "gen";

    private final SecretKey key;
    private final long accessMs;
    private final long refreshMs;

    public JwtService(@Value("${echo.jwt.secret}") String secret,
                      @Value("${echo.jwt.access-minutes:15}") long accessMinutes,
                      @Value("${echo.jwt.refresh-days:30}") long refreshDays) {
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) throw new IllegalStateException("echo.jwt.secret must be at least 32 characters (see application-local.properties).");
        this.key = Keys.hmacShaKeyFor(bytes);
        this.accessMs = accessMinutes * 60_000L;
        this.refreshMs = refreshDays * 86_400_000L;
    }

    long refreshMs() { return refreshMs; }

    String issueAccess(AppUser u)  { return issue(u, TYPE_ACCESS, accessMs); }

    String issueRefresh(AppUser u) { return issue(u, TYPE_REFRESH, refreshMs); }

    private String issue(AppUser u, String type, long life) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
            .subject(String.valueOf(u.id()))
            .claim(CLAIM_NAME, u.username())
            .claim(CLAIM_TYPE, type)
            .claim(CLAIM_GEN, u.sessionGeneration())
            .issuedAt(new Date(now))
            .expiration(new Date(now + life))
            .signWith(key)
            .compact();
    }

    /** The claims of a valid token of the given type, or null. */
    Claims validate(String token, String type) {
        if (token == null || token.isBlank()) return null;
        try {
            Claims c = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
            return type.equals(c.get(CLAIM_TYPE, String.class)) ? c : null;
        } catch (JwtException | IllegalArgumentException ex) {
            return null;
        }
    }

    static int userId(Claims c)     { return Integer.parseInt(c.getSubject()); }
    static String username(Claims c){ return c.get(CLAIM_NAME, String.class); }
    static int generation(Claims c) { Number n = c.get(CLAIM_GEN, Number.class); return n == null ? 0 : n.intValue(); }
}
