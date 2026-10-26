SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_GmailClientCheck_Finish') IS NOT NULL DROP PROCEDURE dbo.stp_GmailClientCheck_Finish
GO
CREATE PROCEDURE dbo.stp_GmailClientCheck_Finish
    @prmGmailClientCheckID INT,
    @prmMessagesListed     INT,
    @prmMessagesAdded      INT,
    @prmIsComplete         BIT,
    @prmErrorText          NVARCHAR(1000) = NULL
AS

/*

Gmail Client (Story 5569): records how a Check mail ended. Returns the check.

TEST
----

    BEGIN TRAN; EXEC dbo.stp_GmailClientCheck_Finish 1, 10, 2, 1; ROLLBACK

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------
2026-10-01  Felix        New (Story 5569, Gmail Client).
2026-10-26  Felix        Story 5654: echoed from Leonardo for Echo's Email Client (Outlook as the mail
                         source). Unchanged.

*/

SET NOCOUNT ON

UPDATE dbo.GmailClientCheck
SET    FinishedDate   = GETDATE(),
       MessagesListed = ISNULL(@prmMessagesListed, 0),
       MessagesAdded  = ISNULL(@prmMessagesAdded, 0),
       IsComplete     = ISNULL(@prmIsComplete, 0),
       ErrorText      = LEFT(NULLIF(@prmErrorText, N''), 1000)
WHERE  GmailClientCheckID = @prmGmailClientCheckID

SELECT GmailClientCheckID, StartedDate, FinishedDate, WindowStartDate, MessagesListed, MessagesAdded, IsComplete, ErrorText
FROM   dbo.GmailClientCheck
WHERE  GmailClientCheckID = @prmGmailClientCheckID

GO
