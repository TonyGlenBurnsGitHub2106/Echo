SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.trg_ReaderPosition_Delete') IS NOT NULL DROP TRIGGER dbo.trg_ReaderPosition_Delete
GO
CREATE TRIGGER dbo.trg_ReaderPosition_Delete ON dbo.ReaderPosition AFTER DELETE AS
BEGIN SET NOCOUNT ON;
    INSERT INTO dbo.ReaderPosition_LOG (IsInsertion, DeletedDate, ReaderPositionID, DocKey, DocTitle,
        ViewMode, BlockIndex, CharOffset, PageNumber, ScrollRatio, PercentRead, CreatedDate, ModifiedDate)
    SELECT 0, GETDATE(), ReaderPositionID, DocKey, DocTitle,
        ViewMode, BlockIndex, CharOffset, PageNumber, ScrollRatio, PercentRead, CreatedDate, ModifiedDate
    FROM Deleted; END;

GO
