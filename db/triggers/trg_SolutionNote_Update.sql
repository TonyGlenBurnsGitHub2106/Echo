SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.trg_SolutionNote_Update') IS NOT NULL DROP TRIGGER dbo.trg_SolutionNote_Update
GO
/*

Trigger trg_SolutionNote_Update on dbo.SolutionNote.

VERSION HISTORY

Date        Developer    Description
----------  -----------  ----------------------------------------------------------
2026-09-17  Felix        Story 3493: audit row into SolutionNote_LOG (the Solutions family).

2026-09-22  Felix        Solutions documents are now NOTES (SolutionDocument* -> SolutionNote*, Tony 09/22/2026).

*/

CREATE TRIGGER dbo.trg_SolutionNote_Update ON dbo.SolutionNote AFTER UPDATE AS
BEGIN SET NOCOUNT ON;
    INSERT INTO dbo.SolutionNote_LOG (IsInsertion, SolutionNoteID, SolutionID, SolutionProjectID, SolutionNoteTypeID, NoteTitle, CurrentVersionNumber, SortOrder, CreatedDate, ModifiedDate, ModifiedBy)
    SELECT 0, SolutionNoteID, SolutionID, SolutionProjectID, SolutionNoteTypeID, NoteTitle, CurrentVersionNumber, SortOrder, CreatedDate, ModifiedDate, ModifiedBy FROM Inserted; END;

GO
