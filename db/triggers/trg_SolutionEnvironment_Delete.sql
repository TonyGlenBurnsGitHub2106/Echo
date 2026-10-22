SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.trg_SolutionEnvironment_Delete') IS NOT NULL DROP TRIGGER dbo.trg_SolutionEnvironment_Delete
GO
/*

Trigger trg_SolutionEnvironment_Delete on dbo.SolutionEnvironment.

VERSION HISTORY

Date        Developer    Description
----------  -----------  ----------------------------------------------------------
2026-09-21  Felix        Story 3516 Solution configuration: audit row into SolutionEnvironment_LOG.

*/

CREATE TRIGGER dbo.trg_SolutionEnvironment_Delete ON dbo.SolutionEnvironment AFTER DELETE AS
BEGIN SET NOCOUNT ON;
    INSERT INTO dbo.SolutionEnvironment_LOG (IsInsertion, DeletedDate, SolutionEnvironmentID, EnvironmentName, CreatedDate)
    SELECT 0, GETDATE(), SolutionEnvironmentID, EnvironmentName, CreatedDate FROM Deleted; END;

GO
