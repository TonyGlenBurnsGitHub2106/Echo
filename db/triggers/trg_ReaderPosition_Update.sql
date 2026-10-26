SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.trg_ReaderPosition_Update') IS NOT NULL DROP TRIGGER dbo.trg_ReaderPosition_Update
GO
CREATE TRIGGER dbo.trg_ReaderPosition_Update ON dbo.ReaderPosition AFTER UPDATE AS
BEGIN SET NOCOUNT ON;
    INSERT INTO dbo.ReaderPosition_LOG (IsInsertion, ReaderPositionID, DocKey, DocTitle, ViewMode,
        BlockIndex, CharOffset, PageNumber, ScrollRatio, PercentRead, CreatedDate, ModifiedDate)
    SELECT 0, ReaderPositionID, DocKey, DocTitle, ViewMode,
        BlockIndex, CharOffset, PageNumber, ScrollRatio, PercentRead, CreatedDate, ModifiedDate
    FROM Inserted; END;

GO
