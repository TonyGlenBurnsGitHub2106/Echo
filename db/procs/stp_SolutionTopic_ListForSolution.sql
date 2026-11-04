SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_SolutionTopic_ListForSolution') IS NOT NULL DROP PROCEDURE dbo.stp_SolutionTopic_ListForSolution
GO

CREATE PROCEDURE dbo.stp_SolutionTopic_ListForSolution
    @prmSolutionID INT,
    @prmSearch     NVARCHAR(200) = NULL,
    @prmOffset     INT = 0,
    @prmPageSize   INT = 50
AS

/*

TEST
----

    EXEC stp_SolutionTopic_ListForSolution 6, NULL, 0, 50

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------------------------------------
11/02/2026  Felix        Solution topics: one page of a solution's topics, by name, searched on the name and the
                         summary. Each carries a 600-character preview of its summary (Truncated says when there
                         is more), how many records are linked to it and when the newest of them changed.

*/

SET NOCOUNT ON

DECLARE @q NVARCHAR(200) = LTRIM(RTRIM(ISNULL(@prmSearch, N'')))
DECLARE @like NVARCHAR(400) = REPLACE(REPLACE(REPLACE(@q, N'[', N'[[]'), N'%', N'[%]'), N'_', N'[_]')
DECLARE @size INT = CASE WHEN ISNULL(@prmPageSize, 0) BETWEEN 1 AND 500 THEN @prmPageSize ELSE 50 END

SELECT  o.SolutionTopicID, o.SolutionID, o.TopicName,
        LEFT(ISNULL(o.TopicSummary, N''), 600) AS TopicSummary,
        CAST(CASE WHEN LEN(ISNULL(o.TopicSummary, N'')) > 600 THEN 1 ELSE 0 END AS BIT) AS Truncated,
        o.ModifiedBy, o.CreatedDate, o.ModifiedDate,
        ISNULL(m.MemberCount, 0) AS MemberCount, m.LatestDate
FROM    dbo.SolutionTopic o
OUTER APPLY (
    SELECT COUNT(*) AS MemberCount, MAX(v.RecordDate) AS LatestDate
    FROM   dbo.SolutionLink k
    JOIN   dbo.vw_SolutionLinkable v
           ON v.SolutionID = k.SolutionID
          AND v.Kind     = CASE WHEN k.FromKind = 'topic' AND k.FromID = o.SolutionTopicID THEN k.ToKind ELSE k.FromKind END
          AND v.RecordID = CASE WHEN k.FromKind = 'topic' AND k.FromID = o.SolutionTopicID THEN k.ToID   ELSE k.FromID   END
    WHERE  k.SolutionID = o.SolutionID
      AND  ((k.FromKind = 'topic' AND k.FromID = o.SolutionTopicID) OR (k.ToKind = 'topic' AND k.ToID = o.SolutionTopicID))
) m
WHERE   o.SolutionID = @prmSolutionID
  AND   (@q = N'' OR o.TopicName LIKE N'%' + @like + N'%' OR o.TopicSummary LIKE N'%' + @like + N'%')
ORDER BY o.TopicName, o.SolutionTopicID
OFFSET ISNULL(@prmOffset, 0) ROWS FETCH NEXT @size ROWS ONLY

GO
