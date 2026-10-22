SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.trg_SolutionNoteType_Delete') IS NOT NULL DROP TRIGGER dbo.trg_SolutionNoteType_Delete
GO
/*

Trigger trg_SolutionNoteType_Delete on dbo.SolutionNoteType.

VERSION HISTORY

Date        Developer    Description
----------  -----------  ----------------------------------------------------------
2026-09-17  Felix        Story 3493: audit row into SolutionNoteType_LOG (the Solutions family).

2026-09-22  Felix        Solutions documents are now NOTES (SolutionDocument* -> SolutionNote*, Tony 09/22/2026).

*/

CREATE TRIGGER dbo.trg_SolutionNoteType_Delete ON dbo.SolutionNoteType AFTER DELETE AS
BEGIN SET NOCOUNT ON;
    INSERT INTO dbo.SolutionNoteType_LOG (IsInsertion, DeletedDate, SolutionNoteTypeID, NoteTypeCode, NoteTypeName, SortOrder, CreatedDate)
    SELECT 0, GETDATE(), SolutionNoteTypeID, NoteTypeCode, NoteTypeName, SortOrder, CreatedDate FROM Deleted; END;

GO
