SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.trg_ReaderHighlight_Delete') IS NOT NULL DROP TRIGGER dbo.trg_ReaderHighlight_Delete
GO
CREATE TRIGGER dbo.trg_ReaderHighlight_Delete ON dbo.ReaderHighlight AFTER DELETE AS
BEGIN SET NOCOUNT ON;
    INSERT INTO dbo.ReaderHighlight_LOG (IsInsertion, DeletedDate, ReaderHighlightID, DocKey, DocTitle, ViewMode,
        PageNumber, BlockIndex, StartOffset, EndOffset, SelectedText, ColorCode, ResearchProjectID, ResearchNoteID,
        CreatedDate, ModifiedDate)
    SELECT 0, GETDATE(), ReaderHighlightID, DocKey, DocTitle, ViewMode,
        PageNumber, BlockIndex, StartOffset, EndOffset, SelectedText, ColorCode, ResearchProjectID, ResearchNoteID,
        CreatedDate, ModifiedDate FROM Deleted; END;

GO
