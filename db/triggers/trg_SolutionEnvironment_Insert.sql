SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.trg_SolutionEnvironment_Insert') IS NOT NULL DROP TRIGGER dbo.trg_SolutionEnvironment_Insert
GO
/*

Trigger trg_SolutionEnvironment_Insert on dbo.SolutionEnvironment.

VERSION HISTORY

Date        Developer    Description
----------  -----------  ----------------------------------------------------------
2026-09-21  Felix        Story 3516 Solution configuration: audit row into SolutionEnvironment_LOG.

*/

CREATE TRIGGER dbo.trg_SolutionEnvironment_Insert ON dbo.SolutionEnvironment AFTER INSERT AS
BEGIN SET NOCOUNT ON;
    INSERT INTO dbo.SolutionEnvironment_LOG (IsInsertion, SolutionEnvironmentID, EnvironmentName, CreatedDate)
    SELECT 1, SolutionEnvironmentID, EnvironmentName, CreatedDate FROM Inserted; END;

GO
