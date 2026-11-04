package com.echo;

import com.echo.SolutionOrientationModels.Activity;
import com.echo.SolutionOrientationModels.LeftOff;
import com.echo.SolutionOrientationModels.LensItem;
import com.echo.SolutionOrientationModels.SummaryAge;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * SQL Server backed {@link SolutionOrientationRepository} (Story 5695).
 */
@Repository
public class JdbcSolutionOrientationRepository implements SolutionOrientationRepository {

    private final SolutionJdbc db;

    public JdbcSolutionOrientationRepository(DataSource ds) { this.db = new SolutionJdbc(new JdbcTemplate(ds)); }

    @Override
    public SummaryAge summaryAge(int solutionId) {
        return SummaryAge.of(db.row("EXEC dbo.stp_Solution_StatusSummaryAge ?", solutionId));
    }

    @Override
    public List<LensItem> lens(int solutionId, String memberCode, String bucket, int offset, int pageSize, int soonDays, int idleDays) {
        return db.list("EXEC dbo.stp_SolutionProjectItem_Lens ?, ?, ?, ?, ?, ?, ?", LensItem::of,
            solutionId, memberCode, bucket, offset, pageSize, soonDays, idleDays);
    }

    @Override
    public LeftOff leftOff(int solutionId, String memberCode) {
        return LeftOff.of(db.row("EXEC dbo.stp_SolutionMemberVisit_GetNote ?, ?", solutionId, memberCode));
    }

    @Override
    public void saveLeftOff(int solutionId, String memberCode, String note) {
        db.row("EXEC dbo.stp_SolutionMemberVisit_SaveNote ?, ?, ?", solutionId, memberCode, note);
    }

    @Override
    public List<Activity> feed(int solutionId, LocalDateTime since, String who, String kind, Integer topicId, int offset, int pageSize) {
        return db.list("EXEC dbo.stp_SolutionActivity_Feed ?, ?, ?, ?, ?, ?, ?", Activity::of,
            solutionId, since == null ? null : Timestamp.valueOf(since), who, kind, topicId, offset, pageSize);
    }

    @Override
    public int feedTotal(int solutionId, LocalDateTime since, String who, String kind, Integer topicId) {
        Map<String, Object> r = db.row("EXEC dbo.stp_SolutionActivity_Feed ?, ?, ?, ?, ?, ?, ?",
            solutionId, since == null ? null : Timestamp.valueOf(since), who, kind, topicId, 0, 1);
        return r == null ? 0 : SolutionRows.intOf(r, "TotalCount");
    }
}
