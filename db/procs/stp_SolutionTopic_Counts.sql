SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_SolutionTopic_Counts') IS NOT NULL DROP PROCEDURE dbo.stp_SolutionTopic_Counts
GO

CREATE PROCEDURE dbo.stp_SolutionTopic_Counts
    @prmSolutionID INT,
    @prmSearch     NVARCHAR(200) = NULL,
    @prmKindCode   VARCHAR(20)   = NULL    -- Story 5695 (Echo)
AS
/*
TEST
----
    EXEC stp_SolutionTopic_Counts 6, NULL
VERSION HISTORY
Date        Developer    Description
----------  -----------  ------------------------------------------------------------------------------------------
11/02/2026  Felix        Solution topics: the counts for the Topics tab under the SAME filter as the list --
                         'total' (what matches) and 'all' (every topic of the solution). Bucket / BucketKey / Cnt.
11/04/2026  Felix        Story 5695 (Echo): the same kind filter as the list, and 'kind:<code>' for each kind under
                         the search alone.
*/
SET NOCOUNT ON
DECLARE @q NVARCHAR(200) = LTRIM(RTRIM(ISNULL(@prmSearch, N'')))
DECLARE @like NVARCHAR(400) = REPLACE(REPLACE(REPLACE(@q, N'[', N'[[]'), N'%', N'[%]'), N'_', N'[_]')
DECLARE @kind VARCHAR(20) = NULLIF(LOWER(LTRIM(RTRIM(ISNULL(@prmKindCode, '')))), '')
;WITH Found AS (
    SELECT o.TopicKindCode
    FROM   dbo.SolutionTopic o
    LEFT JOIN dbo.SolutionTopicType y ON y.SolutionTopicTypeID = o.SolutionTopicTypeID
    WHERE  o.SolutionID = @prmSolutionID
      AND  (@q = N'' OR o.TopicName LIKE N'%' + @like + N'%' OR o.TopicSummary LIKE N'%' + @like + N'%' OR y.TypeName LIKE N'%' + @like + N'%')
)
SELECT 'total' AS Bucket, '' AS BucketKey, COUNT(*) AS Cnt FROM Found WHERE @kind IS NULL OR TopicKindCode = @kind
UNION ALL
SELECT 'kind', TopicKindCode, COUNT(*) FROM Found GROUP BY TopicKindCode
UNION ALL
SELECT 'all', '', COUNT(*) FROM dbo.SolutionTopic WHERE SolutionID = @prmSolutionID

GO
