SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_SolutionTopic_Get') IS NOT NULL DROP PROCEDURE dbo.stp_SolutionTopic_Get
GO

CREATE PROCEDURE dbo.stp_SolutionTopic_Get
    @prmSolutionTopicID INT
AS

/*

TEST
----

    EXEC stp_SolutionTopic_Get 1

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------------------------------------
11/02/2026  Felix        Solution topics: one topic, whole -- its summary, who made it and how many records are linked to it.

*/

SET NOCOUNT ON

SELECT  o.SolutionTopicID, o.SolutionID, o.TopicName, o.TopicSummary,
        CAST(0 AS BIT) AS Truncated,
        a.MemberCode AS AuthorCode, a.MemberName AS AuthorName, o.ModifiedBy,
        o.CreatedDate, o.ModifiedDate,
        (SELECT COUNT(*) FROM dbo.SolutionLink k
         WHERE k.SolutionID = o.SolutionID
           AND ((k.FromKind = 'topic' AND k.FromID = o.SolutionTopicID) OR (k.ToKind = 'topic' AND k.ToID = o.SolutionTopicID))) AS MemberCount
FROM    dbo.SolutionTopic o
LEFT JOIN dbo.SolutionMember a ON a.SolutionMemberID = o.AuthorMemberID
WHERE   o.SolutionTopicID = @prmSolutionTopicID

GO
