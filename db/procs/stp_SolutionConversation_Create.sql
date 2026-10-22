SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_SolutionConversation_Create') IS NOT NULL DROP PROCEDURE dbo.stp_SolutionConversation_Create
GO
CREATE PROCEDURE dbo.stp_SolutionConversation_Create
    @prmSolutionID INT,
    @prmTitle      NVARCHAR(200)
AS

/*

TEST
----

    EXEC stp_SolutionConversation_Create 1, 'Question about the sample'

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------------------------------------
09/17/2026  Felix        Story 3493: open a new conversation thread of type 'agent' and link it to a solution in one
                         transaction. The thread lives in the ordinary Conversation tables. Returns ConversationID.

*/

SET NOCOUNT ON

SET @prmTitle = LTRIM(RTRIM(ISNULL(@prmTitle, '')))
IF @prmTitle = '' BEGIN RAISERROR('A conversation needs a title.', 16, 1) RETURN END
IF NOT EXISTS (SELECT 1 FROM dbo.Solution WHERE SolutionID = @prmSolutionID) BEGIN
    RAISERROR('That solution no longer exists.', 16, 1) RETURN
END

DECLARE @conversationId INT
BEGIN TRY
    BEGIN TRANSACTION
    INSERT INTO dbo.Conversation (Title, ConversationType, ModifiedDate) VALUES (@prmTitle, 'agent', GETDATE())
    SET @conversationId = SCOPE_IDENTITY()
    INSERT INTO dbo.SolutionConversation (SolutionID, ConversationID) VALUES (@prmSolutionID, @conversationId)
    COMMIT TRANSACTION
END TRY
BEGIN CATCH
    IF @@TRANCOUNT > 0 ROLLBACK TRANSACTION
    DECLARE @msg NVARCHAR(2000) = ERROR_MESSAGE()
    RAISERROR(@msg, 16, 1)
    RETURN
END CATCH

SELECT @conversationId AS ConversationID

GO
