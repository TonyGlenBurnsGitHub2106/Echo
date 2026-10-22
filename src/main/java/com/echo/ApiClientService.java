package com.echo;

import com.echo.ApiClientModels.AccessLogRow;
import com.echo.ApiClientModels.ApiClient;
import com.echo.ApiClientModels.Credential;
import com.echo.ApiClientModels.IssuedKey;
import com.echo.ApiClientModels.SolutionLink;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;

/**
 * Business rules for scoped API clients (Story 3493): issuing and revoking
 * keys, checking a presented key, and the record of who may see which solution.
 *
 * A key is 32 bytes of SecureRandom, shown exactly once when it is issued. Only
 * its SHA-256 hash is stored, so a copy of the database cannot be replayed as a
 * credential, and the check compares hashes in constant time.
 */
@Service
public class ApiClientService {

    static final String KEY_PREFIX = "echo_";

    private static final SecureRandom RANDOM = new SecureRandom();

    private final ApiClientRepository repo;

    public ApiClientService(ApiClientRepository repo) { this.repo = repo; }

    // ---- the check the filter makes ----------------------------------------

    /** The client this code-and-key pair belongs to, or null. Disabled and keyless clients are null too. */
    public Credential authenticate(String clientCode, String presentedKey) {
        if (clientCode == null || clientCode.isBlank() || presentedKey == null || presentedKey.isBlank()) return null;
        Credential c = repo.findForAuth(clientCode.trim().toLowerCase());
        // Hash even when the client is unknown, so a miss costs the same as a hit.
        String presentedHash = sha256Hex(presentedKey.trim());
        String storedHash = (c == null || c.keyHash() == null) ? "" : c.keyHash().trim().toLowerCase();
        boolean match = constantTimeEquals(presentedHash, storedHash);
        if (c == null || !c.enabled() || storedHash.isEmpty() || !match) return null;
        return c;
    }

    // ---- administration -----------------------------------------------------

    public List<ApiClient> list() { return repo.list(); }

    public ApiClient find(int id) {
        return repo.list().stream().filter(c -> c.id() == id).findFirst().orElse(null);
    }

    public ApiClient save(int id, String code, String name, String prefixes, String notes, boolean enabled) {
        int savedId = repo.save(id, code, name, prefixes, notes, enabled);
        return savedId <= 0 ? null : find(savedId);
    }

    /** Issue a new key, replacing any old one. The returned key is never recoverable again. */
    public IssuedKey issueKey(int id) {
        ApiClient c = find(id);
        if (c == null) return null;
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String key = KEY_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        String hint = key.substring(key.length() - 4);
        if (!repo.setKey(id, sha256Hex(key), hint)) return null;
        return new IssuedKey(id, c.clientCode(), key, hint);
    }

    public boolean revokeKey(int id)                 { return repo.setKey(id, null, null); }
    public boolean setEnabled(int id, boolean on)    { return repo.setEnabled(id, on); }
    public boolean delete(int id)                    { return repo.delete(id); }

    public List<SolutionLink> solutionsForClient(int id)       { return repo.solutionsForClient(id); }
    public List<SolutionLink> clientsForSolution(int solution) { return repo.clientsForSolution(solution); }
    public boolean setSolutionLink(int id, int solutionId, boolean linked) { return repo.setSolutionLink(id, solutionId, linked); }

    public boolean maySee(int apiClientId, int solutionId) {
        return repo.solutionsForClient(apiClientId).stream().anyMatch(l -> l.solutionId() == solutionId);
    }

    public List<AccessLogRow> accessLog(Integer apiClientId, boolean refusedOnly, int offset, int pageSize) {
        return repo.listAccess(apiClientId, refusedOnly, offset, pageSize);
    }

    public void logAccess(Integer apiClientId, String code, String path, String method, int status, boolean allowed, String ip) {
        repo.logAccess(apiClientId, code, path, method, status, allowed, ip);
    }

    // ---- helpers ------------------------------------------------------------

    static String sha256Hex(String s) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(64);
            for (byte b : d) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }
}
