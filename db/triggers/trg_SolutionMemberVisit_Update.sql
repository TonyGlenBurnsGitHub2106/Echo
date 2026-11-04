SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.trg_SolutionMemberVisit_Update') IS NOT NULL DROP TRIGGER dbo.trg_SolutionMemberVisit_Update
GO
/*

Trigger trg_SolutionMemberVisit_Update on dbo.SolutionMemberVisit.

VERSION HISTORY

Date        Developer    Description
----------  -----------  ----------------------------------------------------------
2026-11-02  Felix        Since you were last here: audit row into SolutionMemberVisit_LOG.
2026-11-04  Felix        Story 5695 Release 4: the note for next time (PartingNote, PartingNoteDate).

*/

CREATE TRIGGER dbo.trg_SolutionMemberVisit_Update ON dbo.SolutionMemberVisit AFTER UPDATE AS
BEGIN SET NOCOUNT ON;
    INSERT INTO dbo.SolutionMemberVisit_LOG (IsInsertion, SolutionMemberVisitID, SolutionID, SolutionMemberID, LastSeenDate, PreviousSeenDate, CreatedDate, ModifiedDate, PartingNote, PartingNoteDate)
    SELECT 0, SolutionMemberVisitID, SolutionID, SolutionMemberID, LastSeenDate, PreviousSeenDate, CreatedDate, ModifiedDate, PartingNote, PartingNoteDate FROM Inserted; END;

GO
