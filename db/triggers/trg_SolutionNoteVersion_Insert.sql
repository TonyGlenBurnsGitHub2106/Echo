SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.trg_SolutionNoteVersion_Insert') IS NOT NULL DROP TRIGGER dbo.trg_SolutionNoteVersion_Insert
GO
/*

Trigger trg_SolutionNoteVersion_Insert on dbo.SolutionNoteVersion.

VERSION HISTORY

Date        Developer    Description
----------  -----------  ----------------------------------------------------------
2026-09-17  Felix        Story 3493: audit row into SolutionNoteVersion_LOG (the Solutions family).

2026-09-22  Felix        Solutions documents are now NOTES (SolutionDocument* -> SolutionNote*, Tony 09/22/2026).

*/

CREATE TRIGGER dbo.trg_SolutionNoteVersion_Insert ON dbo.SolutionNoteVersion AFTER INSERT AS
BEGIN SET NOCOUNT ON;
    INSERT INTO dbo.SolutionNoteVersion_LOG (IsInsertion, SolutionNoteVersionID, SolutionNoteID, VersionNumber, NoteBody, ChangeNote, AuthorMemberID, CreatedDate)
    SELECT 1, SolutionNoteVersionID, SolutionNoteID, VersionNumber, NoteBody, ChangeNote, AuthorMemberID, CreatedDate FROM Inserted; END;

GO
