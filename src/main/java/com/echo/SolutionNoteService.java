package com.echo;

import com.echo.SolutionDrafts.NoteDraft;
import com.echo.SolutionDrafts.StepDraft;
import com.echo.SolutionModels.CodeName;
import com.echo.SolutionModels.Note;
import com.echo.SolutionModels.NoteStep;
import com.echo.SolutionModels.NoteVersion;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * A solution's notes (Story 3493) -- "how do you do it": runbooks with
 * ordered steps, strategies, references. Saving never loses wording (every
 * changed body is a new version). Inside a solution it was given, an API client
 * may edit and delete notes and steps.
 */
@Service
public class SolutionNoteService {

    private final SolutionNoteRepository notes;
    private final SolutionLogService         log;
    private final SolutionAccess             access;
    private final SolutionCodeCheck          codes;
    private final SolutionRepository         solutions;

    public SolutionNoteService(SolutionNoteRepository notes, SolutionLogService log, SolutionAccess access, SolutionCodeCheck codes,
                               SolutionRepository solutions) {
        this.notes = notes; this.log = log; this.access = access; this.codes = codes; this.solutions = solutions;
    }

    /**
     * The note kinds in a new order (the Notes tab's heading grips). Kinds are shared by every
     * solution, so only a person may reorder them -- an agent's drag would rearrange Tony's other
     * solutions. The ids may be a subset: the listed kinds trade the places they already hold.
     */
    public List<CodeName> reorderNoteTypes(SolutionCaller caller, List<Integer> ids) {
        access.requireHuman(caller, "Reordering the note kinds");
        solutions.reorderNoteTypes(ids == null ? List.of() : ids);
        return solutions.lookups().noteTypes();
    }

    public List<Note> list(SolutionCaller caller, int solutionId, String typeCode) {
        access.requireSolution(caller, solutionId);
        return notes.listForSolution(solutionId, typeCode);
    }

    public Note get(SolutionCaller caller, int noteId) {
        Note d = noteId <= 0 ? null : notes.find(noteId);
        if (d == null) throw new SolutionNotFoundException("Note");
        access.requireSolution(caller, d.solutionId());
        return d;
    }

    public Note save(SolutionCaller caller, NoteDraft draft) { return save(caller, draft, null); }

    /** typeName is the Kind picker's text: an existing kind by name, or (for a person) a new one. */
    public Note save(SolutionCaller caller, NoteDraft draft, String typeName) {
        if (draft.id() > 0) {
            Note c = get(caller, draft.id());
            draft = new NoteDraft(draft.id(), c.solutionId(), draft.projectId() == null ? c.projectId() : draft.projectId(),
                SolutionService.keep(draft.typeCode(), c.typeCode(), true), SolutionService.keep(draft.title(), c.title(), true),
                draft.body() == null ? c.body() : draft.body(), draft.changeNote(), draft.sortOrder());
        } else {
            access.requireSolution(caller, draft.solutionId());
        }
        String typeCode = codes.noteTypeCode(caller, draft.typeCode(), typeName);
        if (!java.util.Objects.equals(typeCode, draft.typeCode()))
            draft = new NoteDraft(draft.id(), draft.solutionId(), draft.projectId(), typeCode, draft.title(), draft.body(),
                draft.changeNote(), draft.sortOrder());
        codes.noteType(draft.typeCode());
        int id = notes.save(draft, caller.memberCode());
        if (id <= 0) throw new SolutionNotFoundException("Note");
        return notes.find(id);
    }

    public void delete(SolutionCaller caller, int noteId) {
        get(caller, noteId);
        if (!notes.delete(noteId)) throw new SolutionNotFoundException("Note");
    }

    public List<NoteVersion> versions(SolutionCaller caller, int noteId) {
        get(caller, noteId);
        return notes.versions(noteId);
    }

    public NoteVersion version(SolutionCaller caller, int noteId, int versionNumber) {
        get(caller, noteId);
        NoteVersion v = notes.version(noteId, versionNumber);
        if (v == null) throw new SolutionNotFoundException("Version");
        return v;
    }

    // ---- steps ------------------------------------------------------------

    public Note saveStep(SolutionCaller caller, StepDraft draft) {
        Note note = get(caller, draft.noteId());
        if (draft.id() > 0) {
            requireStepOf(draft.noteId(), draft.id());
            final StepDraft sent = draft;
            NoteStep c = note.steps().stream().filter(s -> s.id() == sent.id()).findFirst().orElseThrow();
            draft = new StepDraft(sent.id(), sent.noteId(), sent.stepNumber(), SolutionService.keep(sent.title(), c.title(), true),
                sent.command() == null ? c.command() : sent.command(),
                sent.expectedResult() == null ? c.expectedResult() : sent.expectedResult(),
                sent.notes() == null ? c.notes() : sent.notes(), c.sourceLogId());
        }
        if (notes.saveStep(draft) <= 0) throw new SolutionNotFoundException("Step");
        return notes.find(draft.noteId());
    }

    public Note moveStep(SolutionCaller caller, int noteId, int stepId, int newStepNumber) {
        get(caller, noteId);
        requireStepOf(noteId, stepId);
        if (!notes.moveStep(stepId, newStepNumber)) throw new SolutionNotFoundException("Step");
        return notes.find(noteId);
    }

    public Note deleteStep(SolutionCaller caller, int noteId, int stepId) {
        get(caller, noteId);
        requireStepOf(noteId, stepId);
        if (!notes.deleteStep(stepId)) throw new SolutionNotFoundException("Step");
        return notes.find(noteId);
    }

    /** Promote a journal entry into a step: what we did becomes how to do it again. */
    public Note stepFromLog(SolutionCaller caller, int noteId, int logId) {
        Note d = get(caller, noteId);
        if (log.get(caller, logId).solutionId() != d.solutionId())
            throw new SolutionRuleException("The log entry and the note belong to different solutions.");
        notes.stepFromLog(noteId, logId);
        return notes.find(noteId);
    }

    /** A step id taken from a URL is trusted only once it is shown to belong to the note beside it. */
    private void requireStepOf(int noteId, int stepId) {
        boolean belongs = notes.steps(noteId).stream().map(NoteStep::id).anyMatch(id -> id == stepId);
        if (!belongs) throw new SolutionNotFoundException("Step");
    }
}
