SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.trg_ReaderHighlight_Insert') IS NOT NULL DROP TRIGGER dbo.trg_ReaderHighlight_Insert
GO
CREATE TRIGGER dbo.trg_ReaderHighlight_Insert ON dbo.ReaderHighlight AFTER INSERT AS
BEGIN SET NOCOUNT ON;
    INSERT INTO dbo.ReaderHighlight_LOG (IsInsertion, ReaderHighlightID, DocKey, DocTitle, ViewMode, PageNumber,
        BlockIndex, StartOffset, EndOffset, SelectedText, ColorCode, ResearchProjectID, ResearchNoteID,
        CreatedDate, ModifiedDate)
    SELECT 1, ReaderHighlightID, DocKey, DocTitle, ViewMode, PageNumber,
        BlockIndex, StartOffset, EndOffset, SelectedText, ColorCode, ResearchProjectID, ResearchNoteID,
        CreatedDate, ModifiedDate FROM Inserted; END;

GO
