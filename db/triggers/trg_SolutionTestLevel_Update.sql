SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.trg_SolutionTestLevel_Update') IS NOT NULL DROP TRIGGER dbo.trg_SolutionTestLevel_Update
GO
/*

Trigger trg_SolutionTestLevel_Update on dbo.SolutionTestLevel.

VERSION HISTORY

Date        Developer    Description
----------  -----------  ----------------------------------------------------------
2026-09-21  Felix        Solution testing checklists: audit row into SolutionTestLevel_LOG.

*/

CREATE TRIGGER dbo.trg_SolutionTestLevel_Update ON dbo.SolutionTestLevel AFTER UPDATE AS
BEGIN SET NOCOUNT ON;
    INSERT INTO dbo.SolutionTestLevel_LOG (IsInsertion, SolutionTestLevelID, LevelTitle, CreatedDate)
    SELECT 0, SolutionTestLevelID, LevelTitle, CreatedDate FROM Inserted; END;

GO
