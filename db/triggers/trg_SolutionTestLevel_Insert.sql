SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.trg_SolutionTestLevel_Insert') IS NOT NULL DROP TRIGGER dbo.trg_SolutionTestLevel_Insert
GO
/*

Trigger trg_SolutionTestLevel_Insert on dbo.SolutionTestLevel.

VERSION HISTORY

Date        Developer    Description
----------  -----------  ----------------------------------------------------------
2026-09-21  Felix        Solution testing checklists: audit row into SolutionTestLevel_LOG.

*/

CREATE TRIGGER dbo.trg_SolutionTestLevel_Insert ON dbo.SolutionTestLevel AFTER INSERT AS
BEGIN SET NOCOUNT ON;
    INSERT INTO dbo.SolutionTestLevel_LOG (IsInsertion, SolutionTestLevelID, LevelTitle, CreatedDate)
    SELECT 1, SolutionTestLevelID, LevelTitle, CreatedDate FROM Inserted; END;

GO
