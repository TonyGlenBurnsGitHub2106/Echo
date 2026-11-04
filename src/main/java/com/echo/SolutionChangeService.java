package com.echo;

import com.echo.SolutionChangeModels.BulkResult;
import com.echo.SolutionChangeModels.Promoted;
import com.echo.SolutionChangeModels.Provenance;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Changing many items at once with a reason, and making one record into another (Story 5695, Echo first,
 * Release 5). Anyone who can see the solution may do both, an agent it is shared with too. The procs check every
 * record and value before the first write, so a refusal changes nothing.
 */
@Service
public class SolutionChangeService {

    private final SolutionChangeRepository repo;
    private final SolutionAccess           access;

    public SolutionChangeService(SolutionChangeRepository repo, SolutionAccess access) {
        this.repo = repo; this.access = access;
    }

    /**
     * One field changed on every item named, with a reason that goes in the one log entry it writes. field is
     * status, project, assignee, due, priority or topic; value a status code, a project id, a member code ("" =
     * nobody), a date yyyy-mm-dd ("" = none), a priority 1 to 5, or a topic id.
     */
    public BulkResult bulkChange(SolutionCaller caller, int solutionId, List<Integer> itemIds, String field, String value,
                                 String reason) {
        access.requireSolution(caller, solutionId);
        String f = field == null ? "" : field.trim().toLowerCase();
        if (!SolutionChangeModels.FIELDS.contains(f))
            throw new IllegalArgumentException("field must be one of status, project, assignee, due, priority, topic.");
        Set<Integer> ids = new LinkedHashSet<>();
        if (itemIds != null) for (Integer i : itemIds) if (i != null && i > 0) ids.add(i);
        if (ids.isEmpty()) throw new IllegalArgumentException("Choose the items to change.");
        if (ids.size() > SolutionChangeModels.MAX_ITEMS)
            throw new IllegalArgumentException("At most " + SolutionChangeModels.MAX_ITEMS + " items can be changed at once.");
        String why = reason == null ? "" : reason.trim();
        if (why.isEmpty()) throw new IllegalArgumentException("Say why. The reason goes in the log.");
        if (why.length() > 1000) throw new IllegalArgumentException("A reason can be at most 1,000 characters.");
        String v = value == null ? "" : value.trim();
        if (v.length() > 400) throw new IllegalArgumentException("That value is too long.");
        String csv = ids.stream().map(String::valueOf).collect(Collectors.joining(","));
        return repo.bulkChange(solutionId, csv, f, v, why, caller.memberCode());
    }

    /**
     * One record made from another, the source kept as it was and linked to it: a thread message into an item, a
     * note or a question; a log entry into an item or a note; a note into an item; an item into a note.
     */
    public Promoted promote(SolutionCaller caller, int solutionId, String sourceKind, Integer sourceId, String targetKind,
                            Integer projectId, String title) {
        access.requireSolution(caller, solutionId);
        String s = sourceKind == null ? "" : sourceKind.trim().toLowerCase();
        String t = targetKind == null ? "" : targetKind.trim().toLowerCase();
        if (!SolutionChangeModels.SOURCES.contains(s))
            throw new IllegalArgumentException("sourceKind must be one of message, log, note, item.");
        if (!SolutionChangeModels.TARGETS.contains(t))
            throw new IllegalArgumentException("targetKind must be one of item, note, question.");
        if (sourceId == null || sourceId <= 0) throw new IllegalArgumentException("Say which record to make it from (sourceId).");
        String name = title == null ? null : title.trim();
        if (name != null && name.isEmpty()) name = null;
        if (name != null && name.length() > 400) throw new IllegalArgumentException("A title can be at most 400 characters.");
        Integer project = projectId == null || projectId <= 0 ? null : projectId;
        return repo.promote(solutionId, s, sourceId, t, project, name, caller.memberCode());
    }

    /** Where a record came from and what was made from it. */
    public List<Provenance> provenance(SolutionCaller caller, int solutionId, String kind, Integer recordId) {
        access.requireSolution(caller, solutionId);
        String k = kind == null ? "" : kind.trim().toLowerCase();
        if (!SolutionChangeModels.SOURCES.contains(k) && !SolutionChangeModels.TARGETS.contains(k))
            throw new IllegalArgumentException("kind must be one of message, log, note, item, question.");
        if (recordId == null || recordId <= 0) throw new IllegalArgumentException("Say which record (recordId).");
        return repo.provenance(solutionId, k, recordId);
    }
}
