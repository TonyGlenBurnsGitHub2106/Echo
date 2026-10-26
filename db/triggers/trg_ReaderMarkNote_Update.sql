SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.trg_ReaderMarkNote_Update') IS NOT NULL DROP TRIGGER dbo.trg_ReaderMarkNote_Update
GO
CREATE TRIGGER dbo.trg_ReaderMarkNote_Update ON dbo.ReaderMarkNote AFTER UPDATE AS
BEGIN SET NOCOUNT ON;
    INSERT INTO dbo.ReaderMarkNote_LOG (IsInsertion, ReaderMarkNoteID, ReaderHighlightID, NoteText,
        CreatedDate, ModifiedDate)
    SELECT 0, ReaderMarkNoteID, ReaderHighlightID, NoteText,
        CreatedDate, ModifiedDate FROM Inserted; END;

GO
