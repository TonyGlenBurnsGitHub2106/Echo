SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_GmailClientCheck_Start') IS NOT NULL DROP PROCEDURE dbo.stp_GmailClientCheck_Start
GO
CREATE PROCEDURE dbo.stp_GmailClientCheck_Start
    @prmWindowStartDate DATETIME
AS

/*

Gmail Client (Story 5569): records the start of a Check mail and the window it reads. Returns
GmailClientCheckID.

TEST
----

    BEGIN TRAN; EXEC dbo.stp_GmailClientCheck_Start '2026-09-01'; ROLLBACK

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------
2026-10-01  Felix        New (Story 5569, Gmail Client).
2026-10-26  Felix        Story 5654: echoed from Leonardo for Echo's Email Client (Outlook as the mail
                         source). Unchanged.

*/

SET NOCOUNT ON

IF @prmWindowStartDate IS NULL
BEGIN
    RAISERROR('A check needs the date it reads from. Nothing was saved.', 16, 1)
    RETURN
END

INSERT INTO dbo.GmailClientCheck (WindowStartDate) VALUES (@prmWindowStartDate)

SELECT CAST(SCOPE_IDENTITY() AS INT) AS GmailClientCheckID

GO
