SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_DailyWorkEntry_Delete') IS NOT NULL DROP PROCEDURE dbo.stp_DailyWorkEntry_Delete
GO
CREATE PROCEDURE dbo.stp_DailyWorkEntry_Delete
    @prmDailyWorkEntryID INT
AS

/*

Story 6852 (Daily Work): delete one entry with its contact and link rows, in one transaction.
The contacts and the team themselves are kept. Every row removed is copied to its _LOG table by
the delete triggers. Returns Deleted (1, or 0 when the entry was already gone).

TEST
----

    BEGIN TRAN; EXEC dbo.stp_DailyWorkEntry_Delete @prmDailyWorkEntryID = 1; ROLLBACK

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------
2026-11-25  Felix        New (Story 6852).

*/

SET NOCOUNT ON
SET XACT_ABORT ON

DECLARE @n INT = 0

BEGIN TRAN
    DELETE FROM dbo.DailyWorkEntryContact WHERE DailyWorkEntryID = @prmDailyWorkEntryID
    DELETE FROM dbo.DailyWorkEntryLink    WHERE DailyWorkEntryID = @prmDailyWorkEntryID
    DELETE FROM dbo.DailyWorkEntry        WHERE DailyWorkEntryID = @prmDailyWorkEntryID
    SET @n = @@ROWCOUNT
COMMIT

SELECT @n AS Deleted

GO
