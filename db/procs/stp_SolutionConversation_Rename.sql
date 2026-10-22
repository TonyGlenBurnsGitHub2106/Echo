SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_SolutionConversation_Rename') IS NOT NULL DROP PROCEDURE dbo.stp_SolutionConversation_Rename
GO
CREATE PROCEDURE dbo.stp_SolutionConversation_Rename
    @prmConversationID INT,
    @prmTitle          NVARCHAR(200)
AS

/*

TEST
----

    EXEC stp_SolutionConversation_Rename 0, 'A better title'

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------------------------------------
09/17/2026  Felix        Story 3493: rename a conversation shown in a solution. Touches the title only -- the shared
                         stp_Conversation_Save would also rewrite the links. Returns RowsAffected.

*/

SET NOCOUNT ON

SET @prmTitle = LTRIM(RTRIM(ISNULL(@prmTitle, '')))
IF @prmTitle = '' BEGIN RAISERROR('A conversation needs a title.', 16, 1) RETURN END

UPDATE dbo.Conversation SET Title = @prmTitle, ModifiedDate = GETDATE() WHERE ConversationID = @prmConversationID
SELECT @@ROWCOUNT AS RowsAffected

GO
