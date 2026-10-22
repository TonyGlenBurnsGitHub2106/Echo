package com.echo;

import com.echo.ApiClientModels.AccessLogRow;
import com.echo.ApiClientModels.ApiClient;
import com.echo.ApiClientModels.Credential;
import com.echo.ApiClientModels.SolutionLink;

import java.util.List;

/**
 * Storage for scoped API clients (Story 3493). Two implementations, chosen by
 * {@code leonardo.demo.enabled}: {@link JdbcApiClientRepository} (the
 * stp_ApiClient* / stp_SolutionApiClient* procs) and
 * {@link InMemoryApiClientRepository} (the demo store).
 */
public interface ApiClientRepository {

    List<ApiClient> list();

    /** The row the authentication filter checks a key against, or null when the code is unknown. */
    Credential findForAuth(String clientCode);

    /** Insert (id = 0) or update. Never touches the key. Throws {@link SolutionRuleException} on a refused rule. */
    int save(int id, String clientCode, String clientName, String allowedPathPrefixes, String notes, boolean enabled);

    /** Store a key hash, or null to revoke the key. */
    boolean setKey(int id, String keyHash, String keyHint);

    boolean setEnabled(int id, boolean enabled);

    boolean delete(int id);

    /** One request made with client headers, allowed or refused. Best effort -- never throws. */
    void logAccess(Integer apiClientId, String clientCode, String path, String method, int status, boolean allowed, String ip);

    List<AccessLogRow> listAccess(Integer apiClientId, boolean refusedOnly, int offset, int pageSize);

    List<SolutionLink> solutionsForClient(int apiClientId);

    List<SolutionLink> clientsForSolution(int solutionId);

    boolean setSolutionLink(int apiClientId, int solutionId, boolean linked);
}
