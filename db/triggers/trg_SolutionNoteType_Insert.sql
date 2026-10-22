SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.trg_SolutionNoteType_Insert') IS NOT NULL DROP TRIGGER dbo.trg_SolutionNoteType_Insert
GO
/*

Trigger trg_SolutionNoteType_Insert on dbo.SolutionNoteType.

VERSION HISTORY

Date        Developer    Description
----------  -----------  ----------------------------------------------------------
2026-09-17  Felix        Story 3493: audit row into SolutionNoteType_LOG (the Solutions family).

2026-09-22  Felix        Solutions documents are now NOTES (SolutionDocument* -> SolutionNote*, Tony 09/22/2026).

*/

CREATE TRIGGER dbo.trg_SolutionNoteType_Insert ON dbo.SolutionNoteType AFTER INSERT AS
BEGIN SET NOCOUNT ON;
    INSERT INTO dbo.SolutionNoteType_LOG (IsInsertion, SolutionNoteTypeID, NoteTypeCode, NoteTypeName, SortOrder, CreatedDate)
    SELECT 1, SolutionNoteTypeID, NoteTypeCode, NoteTypeName, SortOrder, CreatedDate FROM Inserted; END;

GO
