SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_DailyWorkEntry_SetFollowUp') IS NOT NULL DROP PROCEDURE dbo.stp_DailyWorkEntry_SetFollowUp
GO
CREATE PROCEDURE dbo.stp_DailyWorkEntry_SetFollowUp
    @prmDailyWorkEntryID INT,
    @prmIsFollowUp       BIT,
    @prmUser             NVARCHAR(100) = NULL
AS

/*

Story 6852 (Daily Work): flag an entry as needing a follow-up, or clear the flag (the flag
on the list row). Returns Updated (1, or 0 when the entry is gone).

TEST
----

    BEGIN TRAN; EXEC dbo.stp_DailyWorkEntry_SetFollowUp @prmDailyWorkEntryID = 1, @prmIsFollowUp = 1; ROLLBACK

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------
2026-11-25  Felix        New (Story 6852).

*/

SET NOCOUNT ON

UPDATE dbo.DailyWorkEntry
SET    IsFollowUp   = ISNULL(@prmIsFollowUp, 0),
       ModifiedBy   = @prmUser,
       ModifiedDate = GETDATE()
WHERE  DailyWorkEntryID = @prmDailyWorkEntryID
  AND  IsFollowUp <> ISNULL(@prmIsFollowUp, 0)

SELECT CASE WHEN EXISTS (SELECT 1 FROM dbo.DailyWorkEntry WHERE DailyWorkEntryID = @prmDailyWorkEntryID) THEN 1 ELSE 0 END AS Updated

GO
