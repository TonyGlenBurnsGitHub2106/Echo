package com.echo;

import java.time.LocalDateTime;
import java.util.Map;

import static com.echo.SolutionRows.boolOf;
import static com.echo.SolutionRows.dateOf;
import static com.echo.SolutionRows.idOrNull;
import static com.echo.SolutionRows.intOf;
import static com.echo.SolutionRows.strOf;

/**
 * Models for scoped API clients (Story 3493). An API client is an outside
 * caller -- an agent that is NOT Tony, Felix or Stewart -- that may reach only
 * the path prefixes on its allowlist, and inside those only the solutions it
 * has been given. Its key is stored as a SHA-256 hash and is never read back.
 */
public final class ApiClientModels {

    private ApiClientModels() {}

    /** What the admin page shows. Never carries the key or its hash. */
    public record ApiClient(int id, String clientCode, String clientName, String keyHint, boolean hasKey,
                            boolean enabled, String allowedPathPrefixes, String clientNotes,
                            LocalDateTime lastUsedDate, LocalDateTime createdDate, LocalDateTime modifiedDate,
                            int solutionCount, int requestsLastDay, int refusedLastDay) {
        static ApiClient of(Map<String, Object> r) {
            return new ApiClient(intOf(r, "ApiClientID"), strOf(r, "ClientCode"), strOf(r, "ClientName"),
                strOf(r, "KeyHint"), boolOf(r, "HasKey"), boolOf(r, "IsEnabled"), strOf(r, "AllowedPathPrefixes"),
                strOf(r, "ClientNotes"), dateOf(r, "LastUsedDate"), dateOf(r, "CreatedDate"), dateOf(r, "ModifiedDate"),
                intOf(r, "SolutionCount"), intOf(r, "RequestsLastDay"), intOf(r, "RefusedLastDay"));
        }
    }

    /** What the authentication filter checks a request against. Stays inside the server. */
    public record Credential(int id, String clientCode, String keyHash, boolean enabled, String allowedPathPrefixes) {
        static Credential of(Map<String, Object> r) {
            return new Credential(intOf(r, "ApiClientID"), strOf(r, "ClientCode"), strOf(r, "KeyHash"),
                boolOf(r, "IsEnabled"), strOf(r, "AllowedPathPrefixes"));
        }
    }

    public record SolutionLink(int apiClientId, int solutionId, String solutionName, String clientCode, String clientName) {
        static SolutionLink of(Map<String, Object> r) {
            return new SolutionLink(intOf(r, "ApiClientID"), intOf(r, "SolutionID"), strOf(r, "SolutionName"),
                strOf(r, "ClientCode"), strOf(r, "ClientName"));
        }
    }

    public record AccessLogRow(int id, Integer apiClientId, String clientCode, String requestPath, String httpMethod,
                               int statusCode, boolean allowed, String clientIp, LocalDateTime createdDate) {
        static AccessLogRow of(Map<String, Object> r) {
            return new AccessLogRow(intOf(r, "ApiClientAccessLogID"), idOrNull(r, "ApiClientID"), strOf(r, "ClientCode"),
                strOf(r, "RequestPath"), strOf(r, "HttpMethod"), intOf(r, "StatusCode"), boolOf(r, "IsAllowed"),
                strOf(r, "ClientIp"), dateOf(r, "CreatedDate"));
        }
    }

    /** The one and only time a key is visible: the response to issuing it. */
    public record IssuedKey(int apiClientId, String clientCode, String key, String keyHint) {}
}
