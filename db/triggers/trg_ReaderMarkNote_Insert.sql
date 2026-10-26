SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.trg_ReaderMarkNote_Insert') IS NOT NULL DROP TRIGGER dbo.trg_ReaderMarkNote_Insert
GO
CREATE TRIGGER dbo.trg_ReaderMarkNote_Insert ON dbo.ReaderMarkNote AFTER INSERT AS
BEGIN SET NOCOUNT ON;
    INSERT INTO dbo.ReaderMarkNote_LOG (IsInsertion, ReaderMarkNoteID, ReaderHighlightID, NoteText,
        CreatedDate, ModifiedDate)
    SELECT 1, ReaderMarkNoteID, ReaderHighlightID, NoteText,
        CreatedDate, ModifiedDate FROM Inserted; END;

GO
