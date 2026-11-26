SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.trg_DailyWorkEntry_Insert') IS NOT NULL DROP TRIGGER dbo.trg_DailyWorkEntry_Insert
GO
/*

Trigger trg_DailyWorkEntry_Insert on dbo.DailyWorkEntry.

VERSION HISTORY

Date        Developer    Description
----------  -----------  ----------------------------------------------------------
2026-11-25  Felix        Story 6852 (Daily Work): audit row into DailyWorkEntry_LOG.

*/

CREATE TRIGGER dbo.trg_DailyWorkEntry_Insert ON dbo.DailyWorkEntry AFTER INSERT AS
BEGIN SET NOCOUNT ON;
    INSERT INTO dbo.DailyWorkEntry_LOG (IsInsertion, DailyWorkEntryID, WorkDate, WorkDone, StoryRef, TeamID, DailyWorkKindID, MinutesSpent, IsFollowUp, Notes, CreatedBy, CreatedDate, ModifiedBy, ModifiedDate)
    SELECT 1, DailyWorkEntryID, WorkDate, WorkDone, StoryRef, TeamID, DailyWorkKindID, MinutesSpent, IsFollowUp, Notes, CreatedBy, CreatedDate, ModifiedBy, ModifiedDate FROM Inserted; END;

GO
