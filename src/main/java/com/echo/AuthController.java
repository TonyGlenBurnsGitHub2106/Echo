package com.echo;

import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Sign in, stay signed in, sign out (Story 5653). The contract LeonardoWeb's auth.js and app.js
 * expect, so both are used unchanged:
 *
 *   POST /api/auth/login            {username, password} -> {accessToken, username, displayName}
 *                                    + the httpOnly refresh cookie
 *   POST /api/auth/refresh          (cookie) -> {accessToken}; 401 when the cookie is dead
 *   POST /api/auth/logout           clears the cookie
 *   POST /api/auth/logout-everywhere  signs this person out of every browser
 *   POST /api/auth/change-password  {currentPassword, newPassword}
 *   POST /api/auth/verify-password  {password} -- the step-up before Admin -> API Clients. A wrong
 *                                    password is 400, never 401: auth.js reads 401 as "signed out".
 *   GET  /api/ping                  who am I -- the dashboard's "is the session good" check
 *
 * A wrong user name and a wrong password answer the same sentence. Five failures in a row lock
 * the account for fifteen minutes (stp_AppUser_RecordLogin).
 */
@RestController
public class AuthController {

    static final String REFRESH_COOKIE = "echo_refresh";
    private static final String BAD_LOGIN = "That user name and password do not match.";

    private final AppUserRepository users;
    private final JwtService jwt;

    public AuthController(AppUserRepository users, JwtService jwt) {
        this.users = users;
        this.jwt = jwt;
    }

    public record LoginRequest(String username, String password) {}
    public record ChangePasswordRequest(String currentPassword, String newPassword) {}

    @PostMapping("/api/auth/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest body, HttpServletRequest req) {
        String name = body == null || body.username() == null ? "" : body.username().trim();
        String pass = body == null ? null : body.password();
        AppUser u = name.isEmpty() ? null : users.byUsername(name);
        if (u == null || !u.active()) return refuse(401, BAD_LOGIN);
        if (u.lockedNow()) return refuse(423, "Too many wrong passwords. Wait fifteen minutes and try again.");
        if (!PasswordHasher.matches(pass, u.passwordHash())) {
            users.recordLogin(u.id(), false);
            return refuse(401, BAD_LOGIN);
        }
        users.recordLogin(u.id(), true);
        return ResponseEntity.ok()
            .header(HttpHeaders.SET_COOKIE, cookie(jwt.issueRefresh(u), jwt.refreshMs(), req).toString())
            .body(tokenBody(u));
    }

    @PostMapping("/api/auth/refresh")
    public ResponseEntity<?> refresh(@CookieValue(name = REFRESH_COOKIE, required = false) String token, HttpServletRequest req) {
        Claims c = jwt.validate(token, JwtService.TYPE_REFRESH);
        AppUser u = c == null ? null : users.byId(JwtService.userId(c));
        if (u == null || !u.active() || JwtService.generation(c) != u.sessionGeneration())
            return ResponseEntity.status(401)
                .header(HttpHeaders.SET_COOKIE, cookie("", 0, req).toString())
                .body(Map.of("ok", false, "error", "Not signed in"));
        return ResponseEntity.ok(tokenBody(u));
    }

    @PostMapping("/api/auth/logout")
    public ResponseEntity<?> logout(HttpServletRequest req) {
        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, cookie("", 0, req).toString()).body(Map.of("ok", true));
    }

    @PostMapping("/api/auth/logout-everywhere")
    public ResponseEntity<?> logoutEverywhere(HttpServletRequest req) {
        AppUser u = signedIn();
        if (u == null) return refuse(401, "Not signed in");
        users.endSessions(u.id());
        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, cookie("", 0, req).toString()).body(Map.of("ok", true));
    }

    @PostMapping("/api/auth/change-password")
    public ResponseEntity<?> changePassword(@RequestBody ChangePasswordRequest body, HttpServletRequest req) {
        AppUser u = signedIn();
        if (u == null) return refuse(401, "Not signed in");
        if (body == null || !PasswordHasher.matches(body.currentPassword(), u.passwordHash()))
            return refuse(400, "Your current password is not right.");
        String next = body.newPassword() == null ? "" : body.newPassword();
        if (next.length() < 8) return refuse(400, "A new password needs at least 8 characters.");
        users.save(u.username(), u.displayName(), PasswordHasher.hash(next));
        AppUser fresh = users.byId(u.id());                 // the save bumped the generation: this browser gets a new cookie
        return ResponseEntity.ok()
            .header(HttpHeaders.SET_COOKIE, cookie(jwt.issueRefresh(fresh), jwt.refreshMs(), req).toString())
            .body(tokenBody(fresh));
    }

    public record VerifyRequest(String password) {}

    @PostMapping("/api/auth/verify-password")
    public ResponseEntity<?> verifyPassword(@RequestBody(required = false) VerifyRequest body) {
        AppUser u = signedIn();
        if (u == null) return refuse(403, "Only a signed-in person may do this.");
        if (u.lockedNow()) return refuse(423, "Too many wrong passwords. Wait fifteen minutes and try again.");
        if (body == null || !PasswordHasher.matches(body.password(), u.passwordHash())) {
            users.recordLogin(u.id(), false);
            return refuse(400, "That password is not right.");
        }
        return ResponseEntity.ok(Map.of("ok", true));
    }

    @GetMapping("/api/ping")
    public ResponseEntity<?> ping() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ok", true);
        m.put("username", a == null ? null : a.getName());
        AppUser u = signedIn();
        m.put("displayName", u == null ? null : u.displayName());
        return ResponseEntity.ok(m);
    }

    /** The signed-in PERSON (never a client or the service key), or null. */
    AppUser signedIn() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a == null || a.getName() == null || "service".equals(a.getName()) || ApiClientPrincipal.isClient(a.getName())) return null;
        return users.byUsername(a.getName());
    }

    private Map<String, Object> tokenBody(AppUser u) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ok", true);
        m.put("accessToken", jwt.issueAccess(u));
        m.put("username", u.username());
        m.put("displayName", u.displayName());
        return m;
    }

    private static ResponseCookie cookie(String value, long lifeMs, HttpServletRequest req) {
        return ResponseCookie.from(REFRESH_COOKIE, value)
            .httpOnly(true).secure(req.isSecure()).sameSite("Strict").path("/api/auth")
            .maxAge(Duration.ofMillis(lifeMs)).build();
    }

    private static ResponseEntity<?> refuse(int status, String message) {
        return ResponseEntity.status(status).body(Map.of("ok", false, "error", message));
    }
}
