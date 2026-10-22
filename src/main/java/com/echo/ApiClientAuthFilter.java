package com.echo;

import com.echo.ApiClientModels.Credential;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The door for scoped API clients, and the fence around it (Story 3493).
 *
 * Leonardo belongs to Tony, Felix and Stewart. An outside agent gets in ONLY by
 * presenting {@code X-Echo-Client} + {@code X-Echo-Client-Key}, and the moment a
 * request carries either header it is a CLIENT request for the rest of its
 * life. It can end exactly three ways:
 *
 *   401  the code / key pair is wrong, the client is disabled or keyless, or the
 *        request also carries a house credential (X-Echo-Key, Authorization) --
 *        a client may not mix its door with anybody else's;
 *   403  the key is right but the path is outside the client's allowlist
 *        (see {@link ApiClientPathFence} -- deny by default);
 *   the controller's own answer, for a path inside the allowlist.
 *
 * It never falls through to the JWT filter, never becomes the {@code service}
 * principal, and never reaches a static page, the login endpoints, a webhook or
 * any other API. The refusal happens HERE, before Spring Security's matchers and
 * before any controller, so a permitAll rule elsewhere cannot widen a client's
 * reach -- and ApiClientFenceTest walks every route in the application to prove it.
 *
 * Registered first in the security chain by {@link SecurityConfig}. Every client
 * request, allowed or refused, is written to the client access log.
 */
public class ApiClientAuthFilter extends OncePerRequestFilter {

    public static final String HEADER_CLIENT = "X-Echo-Client";
    public static final String HEADER_KEY    = "X-Echo-Client-Key";

    static final String AUTHORITY = "API_CLIENT";

    /** Wrong-key attempts one address may make inside the window before it is turned away unheard. */
    private static final int  MAX_FAILURES = 10;
    private static final long WINDOW_MS    = 15 * 60 * 1000L;

    private final ApiClientService clients;
    private final Map<String, long[]> failures = new ConcurrentHashMap<>();   // ip -> {count, windowStart}

    public ApiClientAuthFilter(ApiClientService clients) { this.clients = clients; }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse resp, FilterChain chain)
            throws ServletException, IOException {

        String code = req.getHeader(HEADER_CLIENT);
        String key  = req.getHeader(HEADER_KEY);
        if (code == null && key == null) {          // not a client request: nothing here applies
            chain.doFilter(req, resp);
            return;
        }

        String uri = req.getRequestURI();
        String ip  = clientIp(req);
        String shownCode = code == null ? "" : code.trim().toLowerCase();

        if (isLockedOut(ip)) {
            refuse(resp, 429, "Too many failed attempts. Try again later.");
            clients.logAccess(null, shownCode, uri, req.getMethod(), 429, false, ip);
            return;
        }
        if (req.getHeader(JwtAuthenticationFilter.HEADER_SERVICE_KEY) != null || req.getHeader("Authorization") != null) {
            refuse(resp, 401, "An API client may not present any other credential.");
            clients.logAccess(null, shownCode, uri, req.getMethod(), 401, false, ip);
            return;
        }

        Credential client = clients.authenticate(code, key);
        if (client == null) {
            recordFailure(ip);
            refuse(resp, 401, "Unknown API client or wrong key.");
            clients.logAccess(null, shownCode, uri, req.getMethod(), 401, false, ip);
            return;
        }
        if (!ApiClientPathFence.allows(client.allowedPathPrefixes(), uri)) {
            refuse(resp, 403, "This API client may not reach that path.");
            clients.logAccess(client.id(), client.clientCode(), uri, req.getMethod(), 403, false, ip);
            return;
        }

        SecurityContext ctx = SecurityContextHolder.createEmptyContext();
        ctx.setAuthentication(new UsernamePasswordAuthenticationToken(
            ApiClientPrincipal.nameOf(client.clientCode()), null, List.of(new SimpleGrantedAuthority(AUTHORITY))));
        SecurityContextHolder.setContext(ctx);
        req.setAttribute(ApiClientPrincipal.REQUEST_ATTRIBUTE, client);
        try {
            chain.doFilter(req, resp);
        } finally {
            clients.logAccess(client.id(), client.clientCode(), uri, req.getMethod(), resp.getStatus(), true, ip);
        }
    }

    private static void refuse(HttpServletResponse resp, int status, String message) throws IOException {
        resp.setStatus(status);
        resp.setContentType("application/json;charset=UTF-8");
        resp.getWriter().write("{\"ok\":false,\"error\":\"" + message + "\"}");
    }

    /** Forget every recorded failure. For tests, which share one address and must not lock each other out. */
    void resetThrottle() { failures.clear(); }

    private boolean isLockedOut(String ip) {
        long[] f = failures.get(ip);
        if (f == null) return false;
        if (System.currentTimeMillis() - f[1] > WINDOW_MS) { failures.remove(ip); return false; }
        return f[0] >= MAX_FAILURES;
    }

    private void recordFailure(String ip) {
        long now = System.currentTimeMillis();
        failures.merge(ip, new long[] { 1, now },
            (old, fresh) -> (now - old[1] > WINDOW_MS) ? fresh : new long[] { old[0] + 1, old[1] });
        if (failures.size() > 5000) failures.clear();   // a flood of addresses must not become a memory leak
    }

    private static String clientIp(HttpServletRequest req) {
        String cf = req.getHeader("CF-Connecting-IP");
        if (cf != null && !cf.isBlank()) return cf.trim();
        String xff = req.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            int comma = xff.indexOf(',');
            return (comma > 0 ? xff.substring(0, comma) : xff).trim();
        }
        return req.getRemoteAddr();
    }
}
