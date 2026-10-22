SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.trg_SolutionConfigSource_Update') IS NOT NULL DROP TRIGGER dbo.trg_SolutionConfigSource_Update
GO
/*

Trigger trg_SolutionConfigSource_Update on dbo.SolutionConfigSource.

VERSION HISTORY

Date        Developer    Description
----------  -----------  ----------------------------------------------------------
2026-09-21  Felix        Story 3516 Solution configuration: audit row into SolutionConfigSource_LOG.

*/

CREATE TRIGGER dbo.trg_SolutionConfigSource_Update ON dbo.SolutionConfigSource AFTER UPDATE AS
BEGIN SET NOCOUNT ON;
    INSERT INTO dbo.SolutionConfigSource_LOG (IsInsertion, SolutionConfigSourceID, SourceTitle, CreatedDate)
    SELECT 0, SolutionConfigSourceID, SourceTitle, CreatedDate FROM Inserted; END;

GO
