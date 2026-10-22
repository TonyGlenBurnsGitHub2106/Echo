package com.echo;

import com.echo.SolutionDrafts.NoteDraft;
import com.echo.SolutionDrafts.StepDraft;
import com.echo.SolutionModels.Note;
import com.echo.SolutionModels.NoteStep;
import com.echo.SolutionModels.NoteVersion;

import java.util.List;

/**
 * Storage for a solution's notes (Story 3493) -- "how do you do it":
 * runbooks with ordered steps, strategies, references. A body is never
 * overwritten; every changed save is a new version. Jdbc = stp_SolutionNote*.
 */
public interface SolutionNoteRepository {

    /** Headers only, with a body preview. */
    List<Note> listForSolution(int solutionId, String typeCode);

    /** One note WITH its current body and its steps. */
    Note find(int id);

    int save(NoteDraft draft, String authorCode);

    boolean delete(int id);

    List<NoteVersion> versions(int noteId);

    NoteVersion version(int noteId, int versionNumber);

    List<NoteStep> steps(int noteId);

    int saveStep(StepDraft draft);

    boolean moveStep(int stepId, int newStepNumber);

    boolean deleteStep(int stepId);

    /** Promote a journal entry into a step of this note; returns the new step id. */
    int stepFromLog(int noteId, int logId);
}
