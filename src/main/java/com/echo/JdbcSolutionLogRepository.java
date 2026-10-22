package com.echo;

import com.echo.SolutionDrafts.LogDraft;
import com.echo.SolutionDrafts.LogFilter;
import com.echo.SolutionModels.LogEntry;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.echo.SolutionRows.intOf;
import static com.echo.SolutionRows.strOf;

/**
 * SQL Server backed {@link SolutionLogRepository} (Story 3493): the
 * stp_SolutionLog* procs. The list and the counts are bound from the same
 * filter, in the same order, so they always describe the same set.
 */
@Repository
public class JdbcSolutionLogRepository implements SolutionLogRepository {

    private final SolutionJdbc db;

    public JdbcSolutionLogRepository(DataSource ds) { this.db = new SolutionJdbc(new JdbcTemplate(ds)); }

    @Override
    public List<LogEntry> list(LogFilter f) {
        return db.list("EXEC dbo.stp_SolutionLog_List ?, ?, ?, ?, ?, ?, ?, ?, ?, ?", LogEntry::of,
            f.solutionId(), f.projectId(), f.itemId(), f.authorCode(), f.typeCode(), f.sinceSequence(), f.search(),
            f.ascending(), f.offset(), f.pageSize());
    }

    @Override
    public Map<String, Integer> counts(LogFilter f) {
        Map<String, Integer> out = new LinkedHashMap<>();
        db.list("EXEC dbo.stp_SolutionLog_Counts ?, ?, ?, ?, ?, ?, ?", r -> r,
                f.solutionId(), f.projectId(), f.itemId(), f.authorCode(), f.typeCode(), f.sinceSequence(), f.search())
          .forEach(r -> {
              String bucket = strOf(r, "Bucket");
              String key = strOf(r, "BucketKey");
              out.put((key == null || key.isEmpty()) ? bucket : bucket + ":" + key, intOf(r, "Cnt"));
          });
        return out;
    }

    @Override
    public LogEntry find(int id) { return db.one("EXEC dbo.stp_SolutionLog_Get ?", LogEntry::of, id); }

    @Override
    public int save(LogDraft d, String authorCode) {
        return db.number("EXEC dbo.stp_SolutionLog_Save ?, ?, ?, ?, ?, ?, ?, ?", "SolutionLogID",
            d.id(), d.solutionId(), d.projectId(), d.itemId(), d.typeCode(), authorCode, d.text(),
            JdbcSolutionRepository.ts(d.logDate()));
    }

    @Override
    public boolean delete(int id) { return db.affected("EXEC dbo.stp_SolutionLog_Delete ?", id); }
}
