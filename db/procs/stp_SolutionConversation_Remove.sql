SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_SolutionConversation_Remove') IS NOT NULL DROP PROCEDURE dbo.stp_SolutionConversation_Remove
GO
CREATE PROCEDURE dbo.stp_SolutionConversation_Remove
    @prmSolutionID     INT,
    @prmConversationID INT
AS

/*

TEST
----

    EXEC stp_SolutionConversation_Remove 0, 0

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------------------------------------
09/17/2026  Felix        Story 3493: unlink a conversation from a solution. The conversation itself is left alone. Returns RowsAffected.

*/

SET NOCOUNT ON

DELETE FROM dbo.SolutionConversation WHERE SolutionID = @prmSolutionID AND ConversationID = @prmConversationID
SELECT @@ROWCOUNT AS RowsAffected

GO
