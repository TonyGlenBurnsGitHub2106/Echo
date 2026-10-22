SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_SolutionConversation_ListForSolution') IS NOT NULL DROP PROCEDURE dbo.stp_SolutionConversation_ListForSolution
GO
CREATE PROCEDURE dbo.stp_SolutionConversation_ListForSolution
    @prmSolutionID INT
AS

/*

TEST
----

    EXEC stp_SolutionConversation_ListForSolution 1

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------------------------------------
09/17/2026  Felix        Story 3493: the conversation threads linked to one solution, busiest first, with who is being
                         waited on. AwaitingFrom is the party the newest unanswered message names; a thread
                         here is never awaiting 'claude', because nothing in the Solutions family may wake the AI.

*/

SET NOCOUNT ON

SELECT  c.ConversationID, c.Title, c.Status, c.ConversationType, c.CreatedDate, c.ModifiedDate,
        (SELECT COUNT(*) FROM dbo.ConversationMessage m WHERE m.ConversationID = c.ConversationID) AS MessageCount,
        (SELECT TOP 1 m.Sender FROM dbo.ConversationMessage m WHERE m.ConversationID = c.ConversationID
         ORDER BY m.ConversationMessageID DESC) AS LastSender,
        (SELECT TOP 1 m.AwaitingResponseFrom FROM dbo.ConversationMessage m
         WHERE m.ConversationID = c.ConversationID AND m.AwaitingResponseFrom IS NOT NULL
         ORDER BY m.ConversationMessageID DESC) AS AwaitingFrom,
        (SELECT COUNT(*) FROM dbo.ConversationMessage m
         WHERE m.ConversationID = c.ConversationID AND m.AwaitingResponseFrom = 'tony') AS AwaitingTonyCount
FROM    dbo.SolutionConversation sc
JOIN    dbo.Conversation c ON c.ConversationID = sc.ConversationID
WHERE   sc.SolutionID = @prmSolutionID
ORDER BY ISNULL(c.ModifiedDate, c.CreatedDate) DESC, c.ConversationID DESC

GO
