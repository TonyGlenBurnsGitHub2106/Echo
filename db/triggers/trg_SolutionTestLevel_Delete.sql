SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.trg_SolutionTestLevel_Delete') IS NOT NULL DROP TRIGGER dbo.trg_SolutionTestLevel_Delete
GO
/*

Trigger trg_SolutionTestLevel_Delete on dbo.SolutionTestLevel.

VERSION HISTORY

Date        Developer    Description
----------  -----------  ----------------------------------------------------------
2026-09-21  Felix        Solution testing checklists: audit row into SolutionTestLevel_LOG.

*/

CREATE TRIGGER dbo.trg_SolutionTestLevel_Delete ON dbo.SolutionTestLevel AFTER DELETE AS
BEGIN SET NOCOUNT ON;
    INSERT INTO dbo.SolutionTestLevel_LOG (IsInsertion, DeletedDate, SolutionTestLevelID, LevelTitle, CreatedDate)
    SELECT 0, GETDATE(), SolutionTestLevelID, LevelTitle, CreatedDate FROM Deleted; END;

GO
