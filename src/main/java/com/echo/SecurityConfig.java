package com.echo;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.util.List;

/**
 * Echo's security chain (Story 5653) -- LeonardoWeb's, less what Echo does not have.
 *
 *   1. {@link ApiClientAuthFilter} first: a request with client headers (an agent such as Rex) is
 *      decided there, against its allowlist, and never falls through.
 *   2. {@link JwtAuthenticationFilter}: the service key or a signed-in person's Bearer token.
 *   3. /api/auth/** and the config checks are open; every other /api path needs one of the
 *      above; static files are open (each page's auth.js sends you to sign in).
 *
 * Unauthenticated API calls answer 401, never 403, because auth.js keys its silent refresh off 401.
 * The CSP is LeonardoWeb's, including its sandbox carry-through for files drawn sandboxed.
 */
@Configuration
public class SecurityConfig {

    private final JwtService jwt;
    private final ApiClientService apiClients;

    @Value("${echo.service.key:}")
    private String serviceKey;

    public SecurityConfig(JwtService jwt, ApiClientService apiClients) {
        this.jwt = jwt;
        this.apiClients = apiClients;
    }

    @Bean
    ApiClientAuthFilter apiClientAuthFilter() { return new ApiClientAuthFilter(apiClients); }

    @Bean
    JwtAuthenticationFilter jwtAuthenticationFilter() { return new JwtAuthenticationFilter(jwt, serviceKey); }

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(e -> e.authenticationEntryPoint((req, res, ex) -> {
                res.setStatus(jakarta.servlet.http.HttpServletResponse.SC_UNAUTHORIZED);
                res.setContentType("application/json;charset=UTF-8");
                res.getWriter().write("{\"error\":\"Not signed in\"}");
            }))
            .headers(h -> h
                .frameOptions(f -> f.sameOrigin())
                .contentTypeOptions(c -> {})
                .addHeaderWriter((req, resp) -> resp.setHeader("Content-Security-Policy", keepSandbox(resp,
                    "default-src 'self'; " +
                    "script-src 'self' 'unsafe-inline' 'wasm-unsafe-eval'; " +
                    "style-src 'self' 'unsafe-inline'; " +
                    "img-src 'self' data: blob: https:; " +
                    "media-src 'self' blob:; " +
                    "font-src 'self' data:; " +
                    "connect-src 'self'; " +
                    "frame-ancestors 'self'; " +
                    "base-uri 'self'; " +
                    "form-action 'self'")))
                .addHeaderWriter((req, resp) -> {
                    if (isVersionedStaticAsset(req.getRequestURI(), req.getQueryString())) {
                        resp.setHeader("Cache-Control", "public, max-age=31536000, immutable");
                        resp.setHeader("Pragma", "cache");
                        resp.setHeader("Expires", "Thu, 31 Dec 2037 23:59:59 GMT");
                    }
                })
                .httpStrictTransportSecurity(sts -> sts.disable()))
            .authorizeHttpRequests(a -> {
                a.requestMatchers("/api/auth/change-password", "/api/auth/logout-everywhere", "/api/auth/verify-password").authenticated();
                a.requestMatchers("/api/auth/**", "/api/system/health", "/api/system/config",
                                  "/api/system/demo/status").permitAll();
                a.requestMatchers("/api/**").authenticated();
                a.anyRequest().permitAll();
            })
            .addFilterBefore(jwtAuthenticationFilter(), UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(apiClientAuthFilter(), JwtAuthenticationFilter.class);
        return http.build();
    }

    private static final List<String> CACHEABLE_EXTENSIONS =
        List.of(".js", ".css", ".png", ".jpg", ".jpeg", ".gif", ".svg", ".ico", ".woff", ".woff2", ".ttf", ".json", ".wasm");

    /** A static file referenced with {@code ?v=N} never changes under that name, so it may be cached hard. */
    static boolean isVersionedStaticAsset(String path, String query) {
        if (path == null || path.startsWith("/api/") || query == null) return false;
        if (!(query.startsWith("v=") || query.contains("&v="))) return false;
        String lower = path.toLowerCase();
        for (String ext : CACHEABLE_EXTENSIONS) if (lower.endsWith(ext)) return true;
        return false;
    }

    /** The site policy, plus "sandbox" when the controller already asked for it on this response. */
    static String keepSandbox(jakarta.servlet.http.HttpServletResponse resp, String policy) {
        String prior = resp.getHeader("Content-Security-Policy");
        return prior != null && prior.contains("sandbox") ? policy + "; sandbox" : policy;
    }
}
