SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_Conversation_SetStatus') IS NOT NULL DROP PROCEDURE dbo.stp_Conversation_SetStatus
GO
CREATE PROCEDURE dbo.stp_Conversation_SetStatus
    @prmConversationID INT,
    @prmStatus         NVARCHAR(20),
    @prmModifiedBy     NVARCHAR(100)
AS
SET NOCOUNT ON

UPDATE  dbo.Conversation
SET     [Status]     = @prmStatus,
        ClosedDate   = CASE WHEN @prmStatus = 'closed' THEN GETDATE() ELSE NULL END,
        ClosedBy     = CASE WHEN @prmStatus = 'closed' THEN @prmModifiedBy ELSE NULL END,
        ModifiedDate = GETDATE()
WHERE   ConversationID = @prmConversationID

-- If we're closing, also clear any "awaiting" flags on hanging messages so the
-- poller doesn't try to answer a closed thread.
IF @prmStatus = 'closed' BEGIN
    UPDATE  dbo.ConversationMessage
    SET     AwaitingResponseFrom = NULL
    WHERE   ConversationID = @prmConversationID AND AwaitingResponseFrom IS NOT NULL
END


GO
