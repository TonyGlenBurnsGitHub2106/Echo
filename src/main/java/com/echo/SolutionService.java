package com.echo;

import com.echo.SolutionDrafts.ProjectDraft;
import com.echo.SolutionDrafts.SolutionDraft;
import com.echo.SolutionModels.Lookups;
import com.echo.SolutionModels.Project;
import com.echo.SolutionModels.Solution;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Solutions and their projects (Story 3493). Every read and write is checked
 * against what the caller may see. A solution SHARED with an API client gives
 * that client full rights inside it -- edit, archive, add and delete. What stays
 * with the owner is the solution's existence and its boundary: creating one,
 * deleting one, sharing it, deciding where its files are saved, and members.
 */
@Service
public class SolutionService {

    private final SolutionRepository repo;
    private final SolutionAccess     access;
    private final SolutionCodeCheck  codes;

    public SolutionService(SolutionRepository repo, SolutionAccess access, SolutionCodeCheck codes) {
        this.repo = repo; this.access = access; this.codes = codes;
    }

    public Lookups lookups() { return repo.lookups(); }

    public List<Solution> list(SolutionCaller caller, boolean includeArchived) {
        return repo.listSolutions(includeArchived, caller.apiClientId());
    }

    public Solution get(SolutionCaller caller, int id) { return access.requireSolution(caller, id); }

    public Solution save(SolutionCaller caller, SolutionDraft draft) {
        String currentOwner = null;
        if (draft.id() <= 0) {
            access.requireHuman(caller, "Only a person may create a solution.");
            draft = merged(draft, null);
        } else {
            Solution current = access.requireSolution(caller, draft.id());   // visibility first: 404, never 403
            refuseClientRename(caller, draft, current);
            currentOwner = current.ownerCode();
            draft = merged(draft, current);
        }
        codes.status(draft.statusCode());
        codes.member(caller, draft.ownerCode(), currentOwner);
        int id = repo.saveSolution(draft, caller.memberCode());
        if (id <= 0) throw new SolutionNotFoundException("Solution");
        return repo.findSolution(id);
    }

    /**
     * A solution's name and alias are the owner's, like sharing it (Story 5766). Both are unique
     * across EVERY solution, so a client allowed to change them could learn, from the refusal,
     * that a solution it was never given holds a name -- and a hidden solution must answer exactly
     * like a missing one. So a client's change is refused whatever the value, before any lookup.
     * Sending the current value unchanged (a full-object PUT) is fine.
     */
    private void refuseClientRename(SolutionCaller caller, SolutionDraft d, Solution c) {
        if (caller == null || !caller.isClient()) return;
        boolean renames = d.name() != null && !d.name().trim().isEmpty() && !d.name().trim().equals(c.name() == null ? "" : c.name().trim());
        boolean realias = d.alias() != null && !alias(d.alias()).equals(c.alias() == null ? "" : c.alias());
        if (renames || realias)
            access.requireHuman(caller, "Only the owner may rename a solution or change its alias. Nothing was saved.");
    }

    public void delete(SolutionCaller caller, int id) {
        access.requireSolution(caller, id);                                   // 404 first: never confirm it exists
        access.requireHuman(caller, "Only a person may delete a solution.");
        if (!repo.deleteSolution(id)) throw new SolutionNotFoundException("Solution");
    }

    /** Where the next attachment is saved. The owner's call; refused while the solution is shared. */
    public Solution setSaveFilesToRepo(SolutionCaller caller, int id, boolean saveToRepo) {
        access.requireSolution(caller, id);
        access.requireHuman(caller, "Only the owner decides where a solution's files are saved.");
        if (!repo.setSaveFilesToRepo(id, saveToRepo, caller.memberCode())) throw new SolutionNotFoundException("Solution");
        return repo.findSolution(id);
    }

    // ---- projects ---------------------------------------------------------

    public List<Project> projects(SolutionCaller caller, int solutionId) {
        access.requireSolution(caller, solutionId);
        return repo.listProjects(solutionId);
    }

    public Project project(SolutionCaller caller, int projectId) {
        Project p = projectId <= 0 ? null : repo.findProject(projectId);
        if (p == null) throw new SolutionNotFoundException("Project");
        access.requireSolution(caller, p.solutionId());
        return p;
    }

    public Project saveProject(SolutionCaller caller, ProjectDraft draft) {
        String currentOwner = null;
        if (draft.id() > 0) {
            Project current = project(caller, draft.id());
            currentOwner = current.ownerCode();
            draft = merged(draft, current);
        } else access.requireSolution(caller, draft.solutionId());
        codes.status(draft.statusCode());
        codes.member(caller, draft.ownerCode(), currentOwner);
        int id = repo.saveProject(draft, caller.memberCode());
        if (id <= 0) throw new SolutionNotFoundException("Project");
        return repo.findProject(id);
    }

    /**
     * Work below woke this project: "not started" becomes "in progress" and nothing else changes
     * (Story 5766). Only the status moves, so no code check can refuse it after the item that woke
     * it was already saved -- the item's save must never answer "Nothing was saved" when it was.
     * A project past "not started" is left alone. A failure is logged, never thrown.
     */
    public void wakeProject(SolutionCaller caller, int projectId, String fromStatus, String toStatus) {
        try {
            Project p = project(caller, projectId);
            if (!fromStatus.equals(p.statusCode())) return;
            repo.saveProject(merged(new ProjectDraft(p.id(), p.solutionId(), null, null, toStatus, null, 0), p), caller.memberCode());
        } catch (RuntimeException ex) {
            org.slf4j.LoggerFactory.getLogger(SolutionService.class)
                .warn("Solutions: project {} was not moved to {} after work below it: {}", projectId, toStatus, ex.getMessage());
        }
    }

    public void deleteProject(SolutionCaller caller, int projectId) {
        project(caller, projectId);     // inside a solution the caller may see, a client may delete too
        if (!repo.deleteProject(projectId)) throw new SolutionNotFoundException("Project");
    }

    // ---- merge: a null field on an update keeps its current value ------------

    private static SolutionDraft merged(SolutionDraft d, Solution c) {
        if (c == null) return new SolutionDraft(0, d.name(), d.description(), d.statusCode(), d.ownerCode(),
            d.statusSummary(), d.targetDate(), d.archived() != null && d.archived(), d.sortOrder(), alias(d.alias()), false);
        return new SolutionDraft(d.id(), keep(d.name(), c.name(), true), keep(d.description(), c.description(), false),
            keep(d.statusCode(), c.statusCode(), true), keep(d.ownerCode(), c.ownerCode(), false),
            keep(d.statusSummary(), c.statusSummary(), false), date(d.targetDate(), d.clearTargetDate(), c.targetDate()),
            d.archived() == null ? c.archived() : d.archived(), d.sortOrder() > 0 ? d.sortOrder() : c.sortOrder(),
            d.alias() == null ? c.alias() : alias(d.alias()), false);
    }

    /** A date on an update: a value replaces, an explicit clear ("" sent) empties, anything else keeps. */
    static java.time.LocalDateTime date(java.time.LocalDateTime sent, boolean clear, java.time.LocalDateTime current) {
        if (sent != null) return sent;
        return clear ? null : current;
    }

    /**
     * An alias is the short handle a person says in place of the name: lower case, letters, digits
     * and hyphens, at least one letter (so it can never be mistaken for an id). Blank clears it.
     */
    static String alias(String sent) {
        String a = sent == null ? "" : sent.trim().toLowerCase(java.util.Locale.ROOT);
        if (a.isEmpty()) return "";
        if (a.length() > 40 || !a.matches("[a-z0-9-]+") || !a.matches(".*[a-z].*"))
            throw new SolutionRuleException("An alias is up to 40 letters, digits and hyphens, with at least one letter -- for example 'claims'.");
        return a;
    }

    /** The solution with this alias, if the caller may see it; otherwise it does not exist. */
    public Solution byAlias(SolutionCaller caller, String alias) {
        String wanted = alias == null ? "" : alias.trim().toLowerCase(java.util.Locale.ROOT);
        return list(caller, true).stream().filter(s -> !wanted.isEmpty() && wanted.equals(s.alias())).findFirst()
            .map(s -> access.requireSolution(caller, s.id()))
            .orElseThrow(() -> new SolutionNotFoundException("Solution"));
    }

    private static ProjectDraft merged(ProjectDraft d, Project c) {
        return new ProjectDraft(d.id(), c.solutionId(), keep(d.name(), c.name(), true), keep(d.description(), c.description(), false),
            keep(d.statusCode(), c.statusCode(), true), keep(d.ownerCode(), c.ownerCode(), false),
            d.sortOrder() > 0 ? d.sortOrder() : c.sortOrder());
    }

    /** @param neverBlank a field that may not be emptied: a blank value keeps the current one too */
    static String keep(String sent, String current, boolean neverBlank) {
        if (sent == null) return current;
        return (neverBlank && sent.trim().isEmpty()) ? current : sent;
    }

    public Lookups saveMember(SolutionCaller caller, int id, String code, String name, boolean agent, boolean active, int sortOrder) {
        access.requireHuman(caller, "Only a person may manage members.");
        repo.saveMember(id, code, name, agent, active, sortOrder);
        return repo.lookups();
    }
}
