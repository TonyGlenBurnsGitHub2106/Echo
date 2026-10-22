SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_Conversation_Save') IS NOT NULL DROP PROCEDURE dbo.stp_Conversation_Save
GO
CREATE PROCEDURE dbo.stp_Conversation_Save
    @prmConversationID    INT,
    @prmTitle             NVARCHAR(200),
    @prmConversationType  NVARCHAR(50),
    @prmLinkedEntityID    INT,
    @prmLinkedTaskID      INT,
    @prmLinkedChecklistID INT
AS
SET NOCOUNT ON

DECLARE @intNewID INT

IF @prmConversationID <= 0 BEGIN
    INSERT INTO dbo.Conversation (Title, ConversationType,
                                  LinkedEntityID, LinkedTaskID, LinkedChecklistID,
                                  ModifiedDate)
    SELECT @prmTitle,
           CASE WHEN @prmConversationType IS NULL OR LEN(@prmConversationType) = 0 THEN 'general' ELSE @prmConversationType END,
           NULLIF(@prmLinkedEntityID, 0),
           NULLIF(@prmLinkedTaskID, 0),
           NULLIF(@prmLinkedChecklistID, 0),
           GETDATE()
    SET @intNewID = SCOPE_IDENTITY()
END ELSE BEGIN
    UPDATE  dbo.Conversation
    SET     Title             = @prmTitle,
            ConversationType  = CASE WHEN @prmConversationType IS NULL OR LEN(@prmConversationType) = 0 THEN ConversationType ELSE @prmConversationType END,
            LinkedEntityID    = NULLIF(@prmLinkedEntityID, 0),
            LinkedTaskID      = NULLIF(@prmLinkedTaskID, 0),
            LinkedChecklistID = NULLIF(@prmLinkedChecklistID, 0),
            ModifiedDate      = GETDATE()
    WHERE   ConversationID = @prmConversationID
    SET @intNewID = @prmConversationID
END

SELECT @intNewID AS ConversationID


GO
