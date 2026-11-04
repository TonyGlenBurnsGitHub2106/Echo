SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.trg_SolutionMemberVisit_Delete') IS NOT NULL DROP TRIGGER dbo.trg_SolutionMemberVisit_Delete
GO
/*

Trigger trg_SolutionMemberVisit_Delete on dbo.SolutionMemberVisit.

VERSION HISTORY

Date        Developer    Description
----------  -----------  ----------------------------------------------------------
2026-11-02  Felix        Since you were last here: audit row into SolutionMemberVisit_LOG.
2026-11-04  Felix        Story 5695 Release 4: the note for next time (PartingNote, PartingNoteDate).

*/

CREATE TRIGGER dbo.trg_SolutionMemberVisit_Delete ON dbo.SolutionMemberVisit AFTER DELETE AS
BEGIN SET NOCOUNT ON;
    INSERT INTO dbo.SolutionMemberVisit_LOG (IsInsertion, DeletedDate, SolutionMemberVisitID, SolutionID, SolutionMemberID, LastSeenDate, PreviousSeenDate, CreatedDate, ModifiedDate, PartingNote, PartingNoteDate)
    SELECT 0, GETDATE(), SolutionMemberVisitID, SolutionID, SolutionMemberID, LastSeenDate, PreviousSeenDate, CreatedDate, ModifiedDate, PartingNote, PartingNoteDate FROM Deleted; END;

GO
