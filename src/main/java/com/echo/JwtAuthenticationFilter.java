package com.echo;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

/**
 * The two ordinary ways into Echo's API (Story 5653), as in LeonardoWeb:
 *
 *   X-Echo-Key      the service key from application-local.properties -- scripts and smoke tests
 *                   on this machine. Principal "service".
 *   Bearer <jwt>    a signed-in person. Principal = the user name.
 *
 * API clients (an agent such as Rex) never come through here: {@link ApiClientAuthFilter} runs
 * first and decides every request that carries client headers.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    public static final String HEADER_SERVICE_KEY = "X-Echo-Key";

    private final JwtService jwt;
    private final String serviceKey;

    public JwtAuthenticationFilter(JwtService jwt, String serviceKey) {
        this.jwt = jwt;
        this.serviceKey = serviceKey == null ? "" : serviceKey.trim();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse resp, FilterChain chain)
            throws ServletException, IOException {
        String key = req.getHeader(HEADER_SERVICE_KEY);
        if (key != null && !serviceKey.isEmpty() && constantTimeEquals(key.trim(), serviceKey)) {
            authenticate("service");
            chain.doFilter(req, resp);
            return;
        }
        String header = req.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            Claims c = jwt.validate(header.substring(7), JwtService.TYPE_ACCESS);
            if (c != null && SecurityContextHolder.getContext().getAuthentication() == null)
                authenticate(JwtService.username(c));
        }
        chain.doFilter(req, resp);
    }

    private static void authenticate(String name) {
        SecurityContext ctx = SecurityContextHolder.createEmptyContext();
        ctx.setAuthentication(new UsernamePasswordAuthenticationToken(name, null, List.of()));
        SecurityContextHolder.setContext(ctx);
    }

    private static boolean constantTimeEquals(String a, String b) {
        byte[] ab = a.getBytes(StandardCharsets.UTF_8), bb = b.getBytes(StandardCharsets.UTF_8);
        return ab.length == bb.length && MessageDigest.isEqual(ab, bb);
    }
}
