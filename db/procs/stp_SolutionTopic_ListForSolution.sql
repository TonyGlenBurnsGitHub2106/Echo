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
    @prmPageSize   INT = 50,
    @prmKindCode   VARCHAR(20)   = NULL    -- Story 5695 (Echo): one kind only; NULL = every kind
AS
/*
TEST
----
    EXEC stp_SolutionTopic_ListForSolution 6, NULL, 0, 50
    EXEC stp_SolutionTopic_ListForSolution 200, NULL, 0, 500, 'part'
VERSION HISTORY
Date        Developer    Description
----------  -----------  ------------------------------------------------------------------------------------------
11/02/2026  Felix        Solution topics: one page of a solution's topics, by name, searched on the name and the
                         summary. Each carries a 600-character preview of its summary (Truncated says when there
                         is more), how many records are linked to it and when the newest of them changed.
11/04/2026  Felix        Story 5695 (Echo): each carries its kind and what that kind carries, and the page comes in
                         the Topics tab's order: the parts first (by kind of part, then in their own order), then
                         the assumptions, the subjects and the tags, each by name. One kind can be asked for.
*/
SET NOCOUNT ON
DECLARE @q NVARCHAR(200) = LTRIM(RTRIM(ISNULL(@prmSearch, N'')))
DECLARE @like NVARCHAR(400) = REPLACE(REPLACE(REPLACE(@q, N'[', N'[[]'), N'%', N'[%]'), N'_', N'[_]')
DECLARE @size INT = CASE WHEN ISNULL(@prmPageSize, 0) BETWEEN 1 AND 500 THEN @prmPageSize ELSE 50 END
DECLARE @kind VARCHAR(20) = NULLIF(LOWER(LTRIM(RTRIM(ISNULL(@prmKindCode, '')))), '')
SELECT  o.SolutionTopicID, o.SolutionID, o.TopicName,
        LEFT(ISNULL(o.TopicSummary, N''), 600) AS TopicSummary,
        CAST(CASE WHEN LEN(ISNULL(o.TopicSummary, N'')) > 600 THEN 1 ELSE 0 END AS BIT) AS Truncated,
        o.ModifiedBy, o.CreatedDate, o.ModifiedDate,
        ISNULL(m.MemberCount, 0) AS MemberCount, m.LatestDate,
        o.TopicKindCode, o.SolutionTopicTypeID, y.TypeName, y.FigureLabel, s.StatusCode, s.StatusName, s.ColorHex,
        CAST(ISNULL(s.IsClosed, 0) AS BIT) AS IsClosed, o.FigureValue, o.AssumptionHolds, o.SortOrder
FROM    dbo.SolutionTopic o
LEFT JOIN dbo.SolutionTopicType y ON y.SolutionTopicTypeID = o.SolutionTopicTypeID
LEFT JOIN dbo.SolutionStatus s ON s.SolutionStatusID = o.SolutionStatusID
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
  AND   (@kind IS NULL OR o.TopicKindCode = @kind)
  AND   (@q = N'' OR o.TopicName LIKE N'%' + @like + N'%' OR o.TopicSummary LIKE N'%' + @like + N'%' OR y.TypeName LIKE N'%' + @like + N'%')
ORDER BY CASE o.TopicKindCode WHEN 'part' THEN 0 WHEN 'assumption' THEN 1 WHEN 'subject' THEN 2 ELSE 3 END,
         CASE WHEN o.TopicKindCode = 'part' THEN ISNULL(y.SortOrder, 2147483647) ELSE 0 END,
         CASE WHEN o.TopicKindCode = 'part' THEN ISNULL(y.TypeName, N'') ELSE N'' END,
         CASE WHEN o.TopicKindCode = 'part' THEN o.SortOrder ELSE 0 END,
         o.TopicName, o.SolutionTopicID
OFFSET ISNULL(@prmOffset, 0) ROWS FETCH NEXT @size ROWS ONLY

GO
