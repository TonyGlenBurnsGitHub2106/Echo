SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.trg_Team_Delete') IS NOT NULL DROP TRIGGER dbo.trg_Team_Delete
GO
/*

Trigger trg_Team_Delete on dbo.Team.

VERSION HISTORY

Date        Developer    Description
----------  -----------  ----------------------------------------------------------
2026-11-25  Felix        Story 6852 (Daily Work): audit row into Team_LOG.

*/

CREATE TRIGGER dbo.trg_Team_Delete ON dbo.Team AFTER DELETE AS
BEGIN SET NOCOUNT ON;
    INSERT INTO dbo.Team_LOG (IsInsertion, DeletedDate, TeamID, TeamName, IsActive, CreatedDate, ModifiedDate)
    SELECT 0, GETDATE(), TeamID, TeamName, IsActive, CreatedDate, ModifiedDate FROM Deleted; END;

GO
