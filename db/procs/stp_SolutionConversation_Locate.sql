SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_SolutionConversation_Locate') IS NOT NULL DROP PROCEDURE dbo.stp_SolutionConversation_Locate
GO
CREATE PROCEDURE dbo.stp_SolutionConversation_Locate
    @prmConversationID INT
AS

/*

TEST
----

    EXEC stp_SolutionConversation_Locate 1

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------------------------------------
09/17/2026  Felix        Story 3493: which solution(s) a conversation is linked to. The API uses it to refuse any
                         conversation that is not part of a solution the caller may see.

*/

SET NOCOUNT ON

SELECT SolutionConversationID, SolutionID, ConversationID
FROM   dbo.SolutionConversation
WHERE  ConversationID = @prmConversationID

GO
