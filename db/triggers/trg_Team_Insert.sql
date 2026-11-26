SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.trg_Team_Insert') IS NOT NULL DROP TRIGGER dbo.trg_Team_Insert
GO
/*

Trigger trg_Team_Insert on dbo.Team.

VERSION HISTORY

Date        Developer    Description
----------  -----------  ----------------------------------------------------------
2026-11-25  Felix        Story 6852 (Daily Work): audit row into Team_LOG.

*/

CREATE TRIGGER dbo.trg_Team_Insert ON dbo.Team AFTER INSERT AS
BEGIN SET NOCOUNT ON;
    INSERT INTO dbo.Team_LOG (IsInsertion, TeamID, TeamName, IsActive, CreatedDate, ModifiedDate)
    SELECT 1, TeamID, TeamName, IsActive, CreatedDate, ModifiedDate FROM Inserted; END;

GO
