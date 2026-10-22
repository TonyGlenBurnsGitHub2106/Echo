SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_ConversationMessage_ListSince') IS NOT NULL DROP PROCEDURE dbo.stp_ConversationMessage_ListSince
GO
CREATE PROCEDURE dbo.stp_ConversationMessage_ListSince
    @prmConversationID INT,
    @prmSinceMessageID INT = 0
AS

/*

TEST
----

    EXEC stp_ConversationMessage_ListSince 1, 0

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------------------------------------
09/17/2026  Felix        Story 3493: the messages of one conversation that are NEWER than a given message id, oldest
                         first. An API client polls with the last id it saw, so it never re-reads the thread.

*/

SET NOCOUNT ON

SELECT ConversationMessageID, ConversationID, Sender, Body, CreatedDate, AwaitingResponseFrom
FROM   dbo.ConversationMessage
WHERE  ConversationID = @prmConversationID
AND    ConversationMessageID > ISNULL(@prmSinceMessageID, 0)
ORDER BY ConversationMessageID

GO
