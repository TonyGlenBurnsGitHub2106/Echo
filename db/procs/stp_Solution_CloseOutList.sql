SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_Solution_CloseOutList') IS NOT NULL DROP PROCEDURE dbo.stp_Solution_CloseOutList
GO
CREATE PROCEDURE dbo.stp_Solution_CloseOutList
    @prmSolutionID INT
AS

/*

TEST
----

    EXEC stp_Solution_CloseOutList 6

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------------------------------------
11/04/2026  Felix        Story 5695 (Echo), Release 6: close-out. Everything still open before a solution is called
                         done, in one list, by group: projects not closed, items still open (by project and order),
                         open questions, proposed decisions, parts not closed, assumptions nobody has settled (still
                         marked holding, so the plan still rests on them), and threads whose last message is waiting
                         for an answer. Each row: GroupCode, the record's Kind and id (a thread: its ConversationID),
                         Title, Sub (where it is, who has it) and when it last changed.

*/

SET NOCOUNT ON

SELECT GroupCode, Kind, RecordID, Title, Sub, ChangedDate
FROM (
    SELECT 1 AS G, CAST('project' AS VARCHAR(10)) AS GroupCode, CAST('project' AS VARCHAR(10)) AS Kind, p.SolutionProjectID AS RecordID,
           CAST(p.ProjectName AS NVARCHAR(400)) AS Title, CAST(s.StatusName AS NVARCHAR(400)) AS Sub,
           ISNULL(p.ModifiedDate, p.CreatedDate) AS ChangedDate, p.SortOrder AS S1, 0 AS S2
    FROM   dbo.SolutionProject p JOIN dbo.SolutionStatus s ON s.SolutionStatusID = p.SolutionStatusID
    WHERE  p.SolutionID = @prmSolutionID AND s.IsClosed = 0
    UNION ALL
    SELECT 2, 'item', 'item', i.SolutionProjectItemID, CAST(i.ItemTitle AS NVARCHAR(400)),
           CAST(p.ProjectName + N' | ' + s.StatusName + ISNULL(N' | ' + m.MemberName, N'') AS NVARCHAR(400)),
           ISNULL(i.ModifiedDate, i.CreatedDate), p.SortOrder, i.SortOrder
    FROM   dbo.SolutionProjectItem i
    JOIN   dbo.SolutionProject p ON p.SolutionProjectID = i.SolutionProjectID
    JOIN   dbo.SolutionStatus s ON s.SolutionStatusID = i.SolutionStatusID
    LEFT JOIN dbo.SolutionMember m ON m.SolutionMemberID = i.AssigneeMemberID
    WHERE  p.SolutionID = @prmSolutionID AND s.IsClosed = 0
    UNION ALL
    SELECT 3, 'question', 'question', q.SolutionQuestionID, CAST(q.QuestionText AS NVARCHAR(400)),
           CAST(ISNULL(N'for ' + m.MemberName, N'nobody named to answer it') AS NVARCHAR(400)),
           ISNULL(q.ModifiedDate, q.CreatedDate), 0, 0
    FROM   dbo.SolutionQuestion q LEFT JOIN dbo.SolutionMember m ON m.SolutionMemberID = q.OwnerMemberID
    WHERE  q.SolutionID = @prmSolutionID AND q.StatusCode = 'open'
    UNION ALL
    SELECT 4, 'decision', 'decision', d.SolutionDecisionID, CAST(d.DecisionTitle AS NVARCHAR(400)), CAST(N'proposed, not yet agreed' AS NVARCHAR(400)),
           ISNULL(d.ModifiedDate, d.CreatedDate), 0, 0
    FROM   dbo.SolutionDecision d
    WHERE  d.SolutionID = @prmSolutionID AND d.StatusCode = 'proposed'
    UNION ALL
    SELECT 5, 'part', 'topic', t.SolutionTopicID, CAST(t.TopicName AS NVARCHAR(400)),
           CAST(ISNULL(ty.TypeName + N' | ', N'') + ISNULL(s.StatusName, N'no status') AS NVARCHAR(400)),
           ISNULL(t.ModifiedDate, t.CreatedDate), ISNULL(ty.SortOrder, 999), t.SortOrder
    FROM   dbo.SolutionTopic t
    LEFT JOIN dbo.SolutionTopicType ty ON ty.SolutionTopicTypeID = t.SolutionTopicTypeID
    LEFT JOIN dbo.SolutionStatus s ON s.SolutionStatusID = t.SolutionStatusID
    WHERE  t.SolutionID = @prmSolutionID AND t.TopicKindCode = 'part' AND ISNULL(s.IsClosed, 0) = 0
    UNION ALL
    SELECT 6, 'assumption', 'topic', t.SolutionTopicID, CAST(t.TopicName AS NVARCHAR(400)), CAST(N'still marked holding' AS NVARCHAR(400)),
           ISNULL(t.ModifiedDate, t.CreatedDate), 0, t.SortOrder
    FROM   dbo.SolutionTopic t
    WHERE  t.SolutionID = @prmSolutionID AND t.TopicKindCode = 'assumption' AND ISNULL(t.AssumptionHolds, 1) = 1
    UNION ALL
    SELECT 7, 'thread', 'message', c.ConversationID, CAST(ISNULL(NULLIF(c.Title, N''), N'Untitled thread') AS NVARCHAR(400)),
           CAST(N'waiting for ' + lm.AwaitingResponseFrom AS NVARCHAR(400)), lm.CreatedDate, 0, 0
    FROM   dbo.SolutionConversation sc
    JOIN   dbo.Conversation c ON c.ConversationID = sc.ConversationID
    CROSS APPLY (SELECT TOP 1 m.AwaitingResponseFrom, m.CreatedDate FROM dbo.ConversationMessage m
                 WHERE m.ConversationID = c.ConversationID ORDER BY m.CreatedDate DESC, m.ConversationMessageID DESC) lm
    WHERE  sc.SolutionID = @prmSolutionID AND ISNULL(lm.AwaitingResponseFrom, N'') <> N'' AND ISNULL(c.Status, N'') <> N'resolved'
) x
ORDER BY G, S1, S2, RecordID

GO
