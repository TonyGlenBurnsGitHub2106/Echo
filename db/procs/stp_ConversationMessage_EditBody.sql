SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_ConversationMessage_EditBody') IS NOT NULL DROP PROCEDURE dbo.stp_ConversationMessage_EditBody
GO
CREATE PROCEDURE dbo.stp_ConversationMessage_EditBody
    @prmConversationMessageID INT,
    @prmBody                  NVARCHAR(MAX)
AS

/*

TEST
----

    EXEC stp_ConversationMessage_EditBody 0, 'Corrected wording'

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------------------------------------
09/17/2026  Felix        Story 3493: change the text of one message and nothing else (the shared Save proc would also
                         rewrite the awaiting flag and the token counts). Returns RowsAffected.

*/

SET NOCOUNT ON

IF LTRIM(RTRIM(ISNULL(@prmBody, ''))) = '' BEGIN RAISERROR('A message needs some text.', 16, 1) RETURN END

UPDATE dbo.ConversationMessage SET Body = @prmBody WHERE ConversationMessageID = @prmConversationMessageID
UPDATE dbo.Conversation SET ModifiedDate = GETDATE() WHERE ConversationID = (SELECT ConversationID FROM dbo.ConversationMessage WHERE ConversationMessageID = @prmConversationMessageID)
SELECT @@ROWCOUNT AS RowsAffected

GO
