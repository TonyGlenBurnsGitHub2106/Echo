SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.trg_ReaderHighlight_Update') IS NOT NULL DROP TRIGGER dbo.trg_ReaderHighlight_Update
GO
CREATE TRIGGER dbo.trg_ReaderHighlight_Update ON dbo.ReaderHighlight AFTER UPDATE AS
BEGIN SET NOCOUNT ON;
    INSERT INTO dbo.ReaderHighlight_LOG (IsInsertion, ReaderHighlightID, DocKey, DocTitle, ViewMode, PageNumber,
        BlockIndex, StartOffset, EndOffset, SelectedText, ColorCode, ResearchProjectID, ResearchNoteID,
        CreatedDate, ModifiedDate)
    SELECT 0, ReaderHighlightID, DocKey, DocTitle, ViewMode, PageNumber,
        BlockIndex, StartOffset, EndOffset, SelectedText, ColorCode, ResearchProjectID, ResearchNoteID,
        CreatedDate, ModifiedDate FROM Inserted; END;

GO
