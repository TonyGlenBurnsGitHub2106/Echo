package com.echo;

import com.echo.SolutionDrafts.LogDraft;
import com.echo.SolutionDrafts.LogFilter;
import com.echo.SolutionModels.LogEntry;
import com.echo.SolutionModels.LogPage;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * A solution's journal (Story 3493) -- "how did we do it". Anyone who can see
 * the solution may append, correct and delete -- a solution shared with an API
 * client gives it full rights inside. Every change and removal still lands in
 * SolutionLog_LOG through the audit triggers, so nothing is ever truly lost.
 */
@Service
public class SolutionLogService {

    private final SolutionLogRepository log;
    private final SolutionAccess        access;
    private final SolutionCodeCheck     codes;

    public SolutionLogService(SolutionLogRepository log, SolutionAccess access, SolutionCodeCheck codes) {
        this.log = log; this.access = access; this.codes = codes;
    }

    public LogPage page(SolutionCaller caller, LogFilter filter) {
        access.requireSolution(caller, filter.solutionId());
        Map<String, Integer> counts = log.counts(filter);
        return new LogPage(log.list(filter), counts, filter.offset(), filter.pageSize(), counts.getOrDefault("total", 0));
    }

    public LogEntry get(SolutionCaller caller, int id) {
        LogEntry e = id <= 0 ? null : log.find(id);
        if (e == null) throw new SolutionNotFoundException("Log entry");
        access.requireSolution(caller, e.solutionId());
        return e;
    }

    public LogEntry save(SolutionCaller caller, LogDraft draft) { return save(caller, draft, null); }

    /** typeName is the Kind picker's text: an existing kind by name, or (for a person) a new one. */
    public LogEntry save(SolutionCaller caller, LogDraft draft, String typeName) {
        if (draft.id() > 0) {
            LogEntry current = get(caller, draft.id());
            draft = new LogDraft(draft.id(), current.solutionId(),
                draft.projectId() == null ? current.projectId() : draft.projectId(),
                draft.itemId() == null ? current.itemId() : draft.itemId(),
                SolutionService.keep(draft.typeCode(), current.typeCode(), true),
                SolutionService.keep(draft.text(), current.text(), true), null);
        } else {
            access.requireSolution(caller, draft.solutionId());
        }
        String typeCode = codes.logTypeCode(caller, draft.typeCode(), typeName);
        if (!java.util.Objects.equals(typeCode, draft.typeCode()))
            draft = new LogDraft(draft.id(), draft.solutionId(), draft.projectId(), draft.itemId(), typeCode, draft.text(), draft.logDate());
        codes.logType(draft.typeCode());
        int id = log.save(draft, caller.memberCode());
        if (id <= 0) throw new SolutionNotFoundException("Log entry");
        return log.find(id);
    }

    public void delete(SolutionCaller caller, int id) {
        get(caller, id);
        if (!log.delete(id)) throw new SolutionNotFoundException("Log entry");
    }
}
