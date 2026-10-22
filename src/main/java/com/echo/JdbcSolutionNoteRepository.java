package com.echo;

import com.echo.SolutionDrafts.NoteDraft;
import com.echo.SolutionDrafts.StepDraft;
import com.echo.SolutionModels.Note;
import com.echo.SolutionModels.NoteStep;
import com.echo.SolutionModels.NoteVersion;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;

/** SQL Server backed {@link SolutionNoteRepository} (Story 3493): the stp_SolutionNote* procs. */
@Repository
public class JdbcSolutionNoteRepository implements SolutionNoteRepository {

    private final SolutionJdbc db;

    public JdbcSolutionNoteRepository(DataSource ds) { this.db = new SolutionJdbc(new JdbcTemplate(ds)); }

    @Override
    public List<Note> listForSolution(int solutionId, String typeCode) {
        return db.list("EXEC dbo.stp_SolutionNote_ListForSolution ?, ?", r -> Note.of(r, List.of()), solutionId, typeCode);
    }

    @Override
    public Note find(int id) {
        Map<String, Object> row = db.row("EXEC dbo.stp_SolutionNote_Get ?", id);
        return row == null ? null : Note.of(row, steps(id));
    }

    @Override
    public int save(NoteDraft d, String authorCode) {
        return db.number("EXEC dbo.stp_SolutionNote_Save ?, ?, ?, ?, ?, ?, ?, ?, ?", "SolutionNoteID",
            d.id(), d.solutionId(), d.projectId(), d.typeCode(), d.title(), d.body(), d.changeNote(), authorCode, d.sortOrder());
    }

    @Override
    public boolean delete(int id) { return db.affected("EXEC dbo.stp_SolutionNote_Delete ?", id); }

    @Override
    public List<NoteVersion> versions(int noteId) {
        return db.list("EXEC dbo.stp_SolutionNoteVersion_List ?", NoteVersion::of, noteId);
    }

    @Override
    public NoteVersion version(int noteId, int versionNumber) {
        return db.one("EXEC dbo.stp_SolutionNoteVersion_Get ?, ?", NoteVersion::of, noteId, versionNumber);
    }

    @Override
    public List<NoteStep> steps(int noteId) {
        return db.list("EXEC dbo.stp_SolutionNoteStep_ListForNote ?", NoteStep::of, noteId);
    }

    @Override
    public int saveStep(StepDraft d) {
        return db.number("EXEC dbo.stp_SolutionNoteStep_Save ?, ?, ?, ?, ?, ?, ?, ?", "SolutionNoteStepID",
            d.id(), d.noteId(), d.stepNumber(), d.title(), d.command(), d.expectedResult(), d.notes(), d.sourceLogId());
    }

    @Override
    public boolean moveStep(int stepId, int newStepNumber) {
        return db.affected("EXEC dbo.stp_SolutionNoteStep_Move ?, ?", stepId, newStepNumber);
    }

    @Override
    public boolean deleteStep(int stepId) { return db.affected("EXEC dbo.stp_SolutionNoteStep_Delete ?", stepId); }

    @Override
    public int stepFromLog(int noteId, int logId) {
        return db.number("EXEC dbo.stp_SolutionNoteStep_FromLog ?, ?", "SolutionNoteStepID", noteId, logId);
    }
}
