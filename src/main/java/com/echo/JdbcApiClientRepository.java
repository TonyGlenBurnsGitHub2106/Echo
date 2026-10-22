package com.echo;

import com.echo.ApiClientModels.AccessLogRow;
import com.echo.ApiClientModels.ApiClient;
import com.echo.ApiClientModels.Credential;
import com.echo.ApiClientModels.SolutionLink;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.util.List;

/**
 * SQL Server backed {@link ApiClientRepository} (Story 3493). EXECs the
 * stp_ApiClient* / stp_SolutionApiClient* procs and nothing else.
 */
@Repository
public class JdbcApiClientRepository implements ApiClientRepository {

    private static final Logger log = LoggerFactory.getLogger(JdbcApiClientRepository.class);

    private final SolutionJdbc db;

    public JdbcApiClientRepository(DataSource ds) { this.db = new SolutionJdbc(new JdbcTemplate(ds)); }

    @Override
    public List<ApiClient> list() { return db.list("EXEC dbo.stp_ApiClient_List", ApiClient::of); }

    @Override
    public Credential findForAuth(String clientCode) {
        return db.one("EXEC dbo.stp_ApiClient_GetForAuth ?", Credential::of, clientCode);
    }

    @Override
    public int save(int id, String clientCode, String clientName, String allowedPathPrefixes, String notes, boolean enabled) {
        return db.number("EXEC dbo.stp_ApiClient_Save ?, ?, ?, ?, ?, ?", "ApiClientID",
            id, clientCode, clientName, allowedPathPrefixes, notes, enabled);
    }

    @Override
    public boolean setKey(int id, String keyHash, String keyHint) {
        return db.affected("EXEC dbo.stp_ApiClient_SetKey ?, ?, ?", id, keyHash, keyHint);
    }

    @Override
    public boolean setEnabled(int id, boolean enabled) {
        return db.affected("EXEC dbo.stp_ApiClient_SetEnabled ?, ?", id, enabled);
    }

    @Override
    public boolean delete(int id) { return db.affected("EXEC dbo.stp_ApiClient_Delete ?", id); }

    @Override
    public void logAccess(Integer apiClientId, String clientCode, String path, String method, int status, boolean allowed, String ip) {
        try {
            db.run("EXEC dbo.stp_ApiClientAccessLog_Insert ?, ?, ?, ?, ?, ?, ?",
                apiClientId == null ? 0 : apiClientId, clientCode, path, method, status, allowed, ip);
        } catch (RuntimeException ex) {
            // The access log must never break the request it is describing.
            log.warn("API client access log write failed: {}", ex.getMessage());
        }
    }

    @Override
    public List<AccessLogRow> listAccess(Integer apiClientId, boolean refusedOnly, int offset, int pageSize) {
        return db.list("EXEC dbo.stp_ApiClientAccessLog_List ?, ?, ?, ?", AccessLogRow::of,
            apiClientId == null ? 0 : apiClientId, refusedOnly, offset, pageSize);
    }

    @Override
    public List<SolutionLink> solutionsForClient(int apiClientId) {
        return db.list("EXEC dbo.stp_SolutionApiClient_ListForClient ?", SolutionLink::of, apiClientId);
    }

    @Override
    public List<SolutionLink> clientsForSolution(int solutionId) {
        return db.list("EXEC dbo.stp_SolutionApiClient_ListForSolution ?", SolutionLink::of, solutionId);
    }

    @Override
    public boolean setSolutionLink(int apiClientId, int solutionId, boolean linked) {
        // stp_Solution_Share, not the bare link proc: sharing must also force the solution's files off the repo.
        return db.affected("EXEC dbo.stp_Solution_Share ?, ?, ?, ?", solutionId, apiClientId, linked, "house");
    }
}
