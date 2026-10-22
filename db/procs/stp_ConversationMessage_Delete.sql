SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_ConversationMessage_Delete') IS NOT NULL DROP PROCEDURE dbo.stp_ConversationMessage_Delete
GO
CREATE PROCEDURE dbo.stp_ConversationMessage_Delete
    @prmConversationMessageID INT
AS

/*

TEST
----

    EXEC stp_ConversationMessage_Delete 0

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------------------------------------
09/17/2026  Felix        Story 3493: remove one message from a conversation (the owner undoing something). Returns RowsAffected.

*/

SET NOCOUNT ON

DELETE FROM dbo.ConversationMessage WHERE ConversationMessageID = @prmConversationMessageID
SELECT @@ROWCOUNT AS RowsAffected

GO
