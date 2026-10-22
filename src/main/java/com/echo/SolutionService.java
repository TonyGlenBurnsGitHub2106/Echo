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
        if (draft.id() <= 0) {
            access.requireHuman(caller, "Only a person may create a solution.");
            draft = merged(draft, null);
        } else {
            draft = merged(draft, access.requireSolution(caller, draft.id()));
        }
        codes.status(draft.statusCode());
        codes.member(caller, draft.ownerCode());
        int id = repo.saveSolution(draft, caller.memberCode());
        if (id <= 0) throw new SolutionNotFoundException("Solution");
        return repo.findSolution(id);
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
        if (draft.id() > 0) draft = merged(draft, project(caller, draft.id()));
        else access.requireSolution(caller, draft.solutionId());
        codes.status(draft.statusCode());
        codes.member(caller, draft.ownerCode());
        int id = repo.saveProject(draft, caller.memberCode());
        if (id <= 0) throw new SolutionNotFoundException("Project");
        return repo.findProject(id);
    }

    public void deleteProject(SolutionCaller caller, int projectId) {
        project(caller, projectId);     // inside a solution the caller may see, a client may delete too
        if (!repo.deleteProject(projectId)) throw new SolutionNotFoundException("Project");
    }

    // ---- merge: a null field on an update keeps its current value ------------

    private static SolutionDraft merged(SolutionDraft d, Solution c) {
        if (c == null) return new SolutionDraft(0, d.name(), d.description(), d.statusCode(), d.ownerCode(),
            d.statusSummary(), d.targetDate(), d.archived() != null && d.archived(), d.sortOrder(), alias(d.alias()));
        return new SolutionDraft(d.id(), keep(d.name(), c.name(), true), keep(d.description(), c.description(), false),
            keep(d.statusCode(), c.statusCode(), true), keep(d.ownerCode(), c.ownerCode(), false),
            keep(d.statusSummary(), c.statusSummary(), false), d.targetDate() == null ? c.targetDate() : d.targetDate(),
            d.archived() == null ? c.archived() : d.archived(), d.sortOrder() > 0 ? d.sortOrder() : c.sortOrder(),
            d.alias() == null ? c.alias() : alias(d.alias()));
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
