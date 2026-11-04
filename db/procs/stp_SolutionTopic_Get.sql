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
11/04/2026  Felix        Story 5695 (Echo): and its kind, with what a part (kind of part, status, number) or an
                         assumption (holds) carries.
*/
SET NOCOUNT ON
SELECT  o.SolutionTopicID, o.SolutionID, o.TopicName, o.TopicSummary,
        CAST(0 AS BIT) AS Truncated,
        a.MemberCode AS AuthorCode, a.MemberName AS AuthorName, o.ModifiedBy,
        o.CreatedDate, o.ModifiedDate,
        (SELECT COUNT(*) FROM dbo.SolutionLink k
         WHERE k.SolutionID = o.SolutionID
           AND ((k.FromKind = 'topic' AND k.FromID = o.SolutionTopicID) OR (k.ToKind = 'topic' AND k.ToID = o.SolutionTopicID))) AS MemberCount,
        CAST(NULL AS DATETIME) AS LatestDate,
        o.TopicKindCode, o.SolutionTopicTypeID, y.TypeName, y.FigureLabel, s.StatusCode, s.StatusName, s.ColorHex,
        CAST(ISNULL(s.IsClosed, 0) AS BIT) AS IsClosed, o.FigureValue, o.AssumptionHolds, o.SortOrder
FROM    dbo.SolutionTopic o
LEFT JOIN dbo.SolutionMember a ON a.SolutionMemberID = o.AuthorMemberID
LEFT JOIN dbo.SolutionTopicType y ON y.SolutionTopicTypeID = o.SolutionTopicTypeID
LEFT JOIN dbo.SolutionStatus s ON s.SolutionStatusID = o.SolutionStatusID
WHERE   o.SolutionTopicID = @prmSolutionTopicID

GO
