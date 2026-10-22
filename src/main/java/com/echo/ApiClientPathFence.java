package com.echo;

/**
 * The one decision the whole API-client design rests on (Story 3493): may a
 * request for this path be served to a client holding this allowlist?
 *
 * Deny is the default and every doubt is a refusal. A path is served only when
 * it is a plain, already-normal path that sits inside one of the client's
 * allowed prefixes. Anything that could make the path the filter sees differ
 * from the path the dispatcher serves -- dot segments, doubled slashes, a
 * backslash, percent-encoding, a matrix parameter -- is refused outright rather
 * than interpreted.
 *
 * Pure and static on purpose, so the rule can be tested exhaustively on its own.
 */
public final class ApiClientPathFence {

    private ApiClientPathFence() {}

    /**
     * Subtrees no API client may ever reach, WHATEVER its allowlist says: sign-in and
     * tokens, service control, client administration, provider webhooks. A typo or a
     * moment of generosity on the admin page cannot open these.
     */
    private static final String[] NEVER = {
        "/api/auth/", "/api/system/", "/api/api-clients/", "/api/webhooks/", "/api/step-up/", "/api/version/"
    };

    public static boolean allows(String allowedPathPrefixes, String requestUri) {
        if (allowedPathPrefixes == null || requestUri == null) return false;
        if (!isPlainPath(requestUri)) return false;
        for (String never : NEVER) {
            if (requestUri.startsWith(never) || (requestUri + "/").equals(never)) return false;
        }
        for (String raw : allowedPathPrefixes.split(";")) {
            String prefix = raw.trim();
            if (!isUsablePrefix(prefix)) continue;
            if (requestUri.startsWith(prefix)) return true;
            // "/api/solutions" is the root of the "/api/solutions/" subtree.
            if ((requestUri + "/").equals(prefix)) return true;
        }
        return false;
    }

    /** A prefix counts only if it names a subtree under /api/ and nothing wider. */
    static boolean isUsablePrefix(String prefix) {
        return prefix.length() >= 7 && prefix.startsWith("/api/") && prefix.endsWith("/") && isPlainPath(prefix);
    }

    static boolean isPlainPath(String path) {
        if (path.isEmpty() || path.charAt(0) != '/') return false;
        if (path.contains("..") || path.contains("//") || path.indexOf('\\') >= 0) return false;
        if (path.indexOf('%') >= 0 || path.indexOf(';') >= 0) return false;
        for (int i = 0; i < path.length(); i++) {
            char c = path.charAt(i);
            if (c <= 0x20 || c >= 0x7F) return false;   // control characters, spaces, anything non-ASCII
        }
        return true;
    }
}
