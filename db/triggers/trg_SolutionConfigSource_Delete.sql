SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.trg_SolutionConfigSource_Delete') IS NOT NULL DROP TRIGGER dbo.trg_SolutionConfigSource_Delete
GO
/*

Trigger trg_SolutionConfigSource_Delete on dbo.SolutionConfigSource.

VERSION HISTORY

Date        Developer    Description
----------  -----------  ----------------------------------------------------------
2026-09-21  Felix        Story 3516 Solution configuration: audit row into SolutionConfigSource_LOG.

*/

CREATE TRIGGER dbo.trg_SolutionConfigSource_Delete ON dbo.SolutionConfigSource AFTER DELETE AS
BEGIN SET NOCOUNT ON;
    INSERT INTO dbo.SolutionConfigSource_LOG (IsInsertion, DeletedDate, SolutionConfigSourceID, SourceTitle, CreatedDate)
    SELECT 0, GETDATE(), SolutionConfigSourceID, SourceTitle, CreatedDate FROM Deleted; END;

GO
