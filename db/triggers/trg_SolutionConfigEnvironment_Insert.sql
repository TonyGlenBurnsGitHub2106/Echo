SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.trg_SolutionConfigEnvironment_Insert') IS NOT NULL DROP TRIGGER dbo.trg_SolutionConfigEnvironment_Insert
GO
/*

Trigger trg_SolutionConfigEnvironment_Insert on dbo.SolutionConfigEnvironment.

VERSION HISTORY

Date        Developer    Description
----------  -----------  ----------------------------------------------------------
2026-09-21  Felix        Story 3516 Solution configuration: audit row into SolutionConfigEnvironment_LOG.

*/

CREATE TRIGGER dbo.trg_SolutionConfigEnvironment_Insert ON dbo.SolutionConfigEnvironment AFTER INSERT AS
BEGIN SET NOCOUNT ON;
    INSERT INTO dbo.SolutionConfigEnvironment_LOG (IsInsertion, SolutionConfigEnvironmentID, SolutionID, SolutionEnvironmentID, SortOrder, CreatedDate)
    SELECT 1, SolutionConfigEnvironmentID, SolutionID, SolutionEnvironmentID, SortOrder, CreatedDate FROM Inserted; END;

GO
