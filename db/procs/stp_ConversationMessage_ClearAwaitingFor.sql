SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_ConversationMessage_ClearAwaitingFor') IS NOT NULL DROP PROCEDURE dbo.stp_ConversationMessage_ClearAwaitingFor
GO
CREATE PROCEDURE dbo.stp_ConversationMessage_ClearAwaitingFor
    @prmConversationID INT,
    @prmParty          NVARCHAR(20)
AS

/*

TEST
----

    EXEC stp_ConversationMessage_ClearAwaitingFor 1, 'tony'

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------------------------------------
09/17/2026  Felix        Story 3493: the named party has now read the thread: clear the "awaiting" flag on the messages
                         that were waiting on them, and on no others. Refuses the party 'claude' outright --
                         that flag belongs to Stewart's on-demand path and nothing here may touch it. Returns RowsAffected.

*/

SET NOCOUNT ON

SET @prmParty = LOWER(LTRIM(RTRIM(ISNULL(@prmParty, ''))))
IF @prmParty = '' OR @prmParty = 'claude' BEGIN SELECT 0 AS RowsAffected RETURN END

UPDATE dbo.ConversationMessage SET AwaitingResponseFrom = NULL
WHERE  ConversationID = @prmConversationID AND AwaitingResponseFrom = @prmParty

SELECT @@ROWCOUNT AS RowsAffected

GO
