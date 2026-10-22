SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.trg_SolutionConfigEnvironment_Delete') IS NOT NULL DROP TRIGGER dbo.trg_SolutionConfigEnvironment_Delete
GO
/*

Trigger trg_SolutionConfigEnvironment_Delete on dbo.SolutionConfigEnvironment.

VERSION HISTORY

Date        Developer    Description
----------  -----------  ----------------------------------------------------------
2026-09-21  Felix        Story 3516 Solution configuration: audit row into SolutionConfigEnvironment_LOG.

*/

CREATE TRIGGER dbo.trg_SolutionConfigEnvironment_Delete ON dbo.SolutionConfigEnvironment AFTER DELETE AS
BEGIN SET NOCOUNT ON;
    INSERT INTO dbo.SolutionConfigEnvironment_LOG (IsInsertion, DeletedDate, SolutionConfigEnvironmentID, SolutionID, SolutionEnvironmentID, SortOrder, CreatedDate)
    SELECT 0, GETDATE(), SolutionConfigEnvironmentID, SolutionID, SolutionEnvironmentID, SortOrder, CreatedDate FROM Deleted; END;

GO
