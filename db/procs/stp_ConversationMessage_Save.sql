SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_ConversationMessage_Save') IS NOT NULL DROP PROCEDURE dbo.stp_ConversationMessage_Save
GO
CREATE PROCEDURE dbo.stp_ConversationMessage_Save
    @prmConversationMessageID INT,
    @prmConversationID        INT,
    @prmSender                NVARCHAR(20),
    @prmBody                  NVARCHAR(MAX),
    @prmAwaitingResponseFrom  NVARCHAR(20),
    @prmTokensIn              INT,
    @prmTokensOut             INT,
    @prmDurationMs            INT,
    @prmModelUsed             NVARCHAR(50),
    @prmErrorText             NVARCHAR(MAX)
AS
SET NOCOUNT ON

DECLARE @intNewID INT

IF @prmConversationMessageID <= 0 BEGIN
    INSERT INTO dbo.ConversationMessage (ConversationID, Sender, Body, AwaitingResponseFrom,
                                         TokensIn, TokensOut, DurationMs, ModelUsed, ErrorText)
    SELECT @prmConversationID, @prmSender, @prmBody, NULLIF(@prmAwaitingResponseFrom, ''),
           NULLIF(@prmTokensIn, 0), NULLIF(@prmTokensOut, 0), NULLIF(@prmDurationMs, 0),
           NULLIF(@prmModelUsed, ''), NULLIF(@prmErrorText, '')
    SET @intNewID = SCOPE_IDENTITY()

    -- Bump parent ModifiedDate so the list page sorts naturally and the poller's "any work?" query is cheap.
    UPDATE dbo.Conversation SET ModifiedDate = GETDATE() WHERE ConversationID = @prmConversationID
END ELSE BEGIN
    UPDATE  dbo.ConversationMessage
    SET     Body                 = @prmBody,
            AwaitingResponseFrom = NULLIF(@prmAwaitingResponseFrom, ''),
            TokensIn             = NULLIF(@prmTokensIn, 0),
            TokensOut            = NULLIF(@prmTokensOut, 0),
            DurationMs           = NULLIF(@prmDurationMs, 0),
            ModelUsed            = NULLIF(@prmModelUsed, ''),
            ErrorText            = NULLIF(@prmErrorText, '')
    WHERE   ConversationMessageID = @prmConversationMessageID
    SET @intNewID = @prmConversationMessageID
END

SELECT @intNewID AS ConversationMessageID


GO
