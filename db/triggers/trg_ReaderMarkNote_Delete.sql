SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.trg_ReaderMarkNote_Delete') IS NOT NULL DROP TRIGGER dbo.trg_ReaderMarkNote_Delete
GO
CREATE TRIGGER dbo.trg_ReaderMarkNote_Delete ON dbo.ReaderMarkNote AFTER DELETE AS
BEGIN SET NOCOUNT ON;
    INSERT INTO dbo.ReaderMarkNote_LOG (IsInsertion, DeletedDate, ReaderMarkNoteID, ReaderHighlightID,
        NoteText, CreatedDate, ModifiedDate)
    SELECT 0, GETDATE(), ReaderMarkNoteID, ReaderHighlightID,
        NoteText, CreatedDate, ModifiedDate FROM Deleted; END;

GO
