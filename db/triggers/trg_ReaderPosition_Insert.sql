SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.trg_ReaderPosition_Insert') IS NOT NULL DROP TRIGGER dbo.trg_ReaderPosition_Insert
GO
CREATE TRIGGER dbo.trg_ReaderPosition_Insert ON dbo.ReaderPosition AFTER INSERT AS
BEGIN SET NOCOUNT ON;
    INSERT INTO dbo.ReaderPosition_LOG (IsInsertion, ReaderPositionID, DocKey, DocTitle, ViewMode,
        BlockIndex, CharOffset, PageNumber, ScrollRatio, PercentRead, CreatedDate, ModifiedDate)
    SELECT 1, ReaderPositionID, DocKey, DocTitle, ViewMode,
        BlockIndex, CharOffset, PageNumber, ScrollRatio, PercentRead, CreatedDate, ModifiedDate
    FROM Inserted; END;

GO
