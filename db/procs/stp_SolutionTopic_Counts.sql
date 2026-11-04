SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_SolutionTopic_Counts') IS NOT NULL DROP PROCEDURE dbo.stp_SolutionTopic_Counts
GO

CREATE PROCEDURE dbo.stp_SolutionTopic_Counts
    @prmSolutionID INT,
    @prmSearch     NVARCHAR(200) = NULL
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

*/

SET NOCOUNT ON

DECLARE @q NVARCHAR(200) = LTRIM(RTRIM(ISNULL(@prmSearch, N'')))
DECLARE @like NVARCHAR(400) = REPLACE(REPLACE(REPLACE(@q, N'[', N'[[]'), N'%', N'[%]'), N'_', N'[_]')

SELECT 'total' AS Bucket, '' AS BucketKey, COUNT(*) AS Cnt
FROM   dbo.SolutionTopic o
WHERE  o.SolutionID = @prmSolutionID
  AND  (@q = N'' OR o.TopicName LIKE N'%' + @like + N'%' OR o.TopicSummary LIKE N'%' + @like + N'%')
UNION ALL
SELECT 'all', '', COUNT(*) FROM dbo.SolutionTopic WHERE SolutionID = @prmSolutionID

GO
