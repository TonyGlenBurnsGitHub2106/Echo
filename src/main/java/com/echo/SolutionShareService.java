package com.echo;

import com.echo.ApiClientModels.ApiClient;
import com.echo.ApiClientModels.SolutionLink;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Sharing a solution (Story 3493). A solution is private to the house until its
 * owner shares it with an API client; from then on that client has FULL rights
 * inside it, and no other solution exists as far as the client can tell.
 *
 * Sharing is the owner's act and only the owner's: a client can never share a
 * solution onward, see who else it is shared with, or un-share it. Sharing also
 * forces the solution's attachments off the repo (in the proc, in the same
 * transaction), because nothing an outside agent can upload may be committed.
 */
@Service
public class SolutionShareService {

    private final ApiClientService clients;
    private final SolutionAccess   access;
    private final SolutionRepository solutions;

    private final String baseUrl;

    public SolutionShareService(ApiClientService clients, SolutionAccess access, SolutionRepository solutions,
                                @org.springframework.beans.factory.annotation.Value("${echo.public.url:http://localhost:8770}") String baseUrl) {
        this.baseUrl = baseUrl;
        this.clients = clients; this.access = access; this.solutions = solutions;
    }

    /** Every API client, each marked with whether this solution is shared with it. */
    public List<Map<String, Object>> shares(SolutionCaller caller, int solutionId) {
        access.requireSolution(caller, solutionId);
        access.requireHuman(caller, "Only the owner can see who a solution is shared with.");
        List<Integer> shared = clients.clientsForSolution(solutionId).stream().map(SolutionLink::apiClientId).toList();
        List<Map<String, Object>> out = new ArrayList<>();
        for (ApiClient c : clients.list()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("apiClientId", c.id());
            row.put("clientCode", c.clientCode());
            row.put("clientName", c.clientName());
            row.put("enabled", c.enabled());
            row.put("hasKey", c.hasKey());
            row.put("shared", shared.contains(c.id()));
            out.add(row);
        }
        return out;
    }

    /**
     * The hand-off text for a client this solution is shared with. The owner's alone, and only
     * for a client that really is shared: anything else answers "not found". There is no key in
     * it -- the server does not have one -- only the slot the page fills in the browser.
     */
    public Map<String, Object> brief(SolutionCaller caller, int solutionId, int apiClientId) {
        access.requireSolution(caller, solutionId);
        access.requireHuman(caller, "Only the owner can read a client's brief.");
        ApiClient c = clients.find(apiClientId);
        boolean shared = clients.clientsForSolution(solutionId).stream().anyMatch(l -> l.apiClientId() == apiClientId);
        if (c == null || !shared) throw new SolutionNotFoundException("API client");
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("apiClientId", c.id());
        out.put("clientName", c.clientName());
        out.put("hasKey", c.hasKey());
        out.put("keyHint", c.keyHint());
        out.put("keySlot", ApiClientBrief.KEY_SLOT);
        out.put("text", ApiClientBrief.textFor(c, baseUrl, solutionId, solutions.findSolution(solutionId).name(), solutions.findSolution(solutionId).alias()));
        return out;
    }

    public Map<String, Object> setShared(SolutionCaller caller, int solutionId, int apiClientId, boolean shared) {
        access.requireSolution(caller, solutionId);
        access.requireHuman(caller, "Only the owner can share a solution.");
        if (clients.find(apiClientId) == null) throw new SolutionNotFoundException("API client");
        boolean wasRepo = solutions.findSolution(solutionId).saveFilesToRepo();
        clients.setSolutionLink(apiClientId, solutionId, shared);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("shares", shares(caller, solutionId));
        out.put("filesMovedOffRepo", shared && wasRepo);
        out.put("solution", solutions.findSolution(solutionId));
        return out;
    }
}
