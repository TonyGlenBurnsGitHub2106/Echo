package com.echo;

import com.echo.SolutionDrafts.LogDraft;
import com.echo.SolutionDrafts.LogFilter;
import com.echo.SolutionModels.LogEntry;

import java.util.List;
import java.util.Map;

/**
 * Storage for a solution's journal (Story 3493) -- "how did we do it": an
 * append-only, sequence-numbered record, paged and counted in SQL. Jdbc =
 * stp_SolutionLog*.
 */
public interface SolutionLogRepository {

    List<LogEntry> list(LogFilter filter);

    /** Counts under the SAME filter: "total", "type:x", "author:x". */
    Map<String, Integer> counts(LogFilter filter);

    /** One entry WITH its full text. */
    LogEntry find(int id);

    /** Append (id = 0) or correct an entry; returns its id. */
    int save(LogDraft draft, String authorCode);

    boolean delete(int id);
}
