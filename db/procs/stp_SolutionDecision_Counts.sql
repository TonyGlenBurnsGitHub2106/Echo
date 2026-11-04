SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_SolutionDecision_Counts') IS NOT NULL DROP PROCEDURE dbo.stp_SolutionDecision_Counts
GO
CREATE PROCEDURE dbo.stp_SolutionDecision_Counts
    @prmSolutionID INT,
    @prmStatusCode VARCHAR(20)   = NULL,
    @prmSearch     NVARCHAR(200) = NULL
AS

/*

TEST
----

    EXEC stp_SolutionDecision_Counts 6, NULL, NULL

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------------------------------------
11/04/2026  Felix        Story 5695 (Echo): the counts for the Decisions view under the SAME filter as the list --
                         'total' (what matches), 'all' (every decision of the solution) and 'status:<code>' for each
                         status under the search alone. Bucket / BucketKey / Cnt.

*/

SET NOCOUNT ON

DECLARE @q NVARCHAR(200) = LTRIM(RTRIM(ISNULL(@prmSearch, N'')))
DECLARE @like NVARCHAR(400) = REPLACE(REPLACE(REPLACE(@q, N'[', N'[[]'), N'%', N'[%]'), N'_', N'[_]')
DECLARE @status VARCHAR(20) = NULLIF(LOWER(LTRIM(RTRIM(ISNULL(@prmStatusCode, '')))), '')

;WITH Found AS (
    SELECT d.StatusCode
    FROM   dbo.SolutionDecision d
    WHERE  d.SolutionID = @prmSolutionID
      AND  (@q = N'' OR d.DecisionTitle LIKE N'%' + @like + N'%' OR d.Context LIKE N'%' + @like + N'%'
                     OR d.OptionsText LIKE N'%' + @like + N'%' OR d.Outcome LIKE N'%' + @like + N'%')
)
SELECT 'total' AS Bucket, '' AS BucketKey, COUNT(*) AS Cnt FROM Found WHERE @status IS NULL OR StatusCode = @status OR (@status = 'current' AND StatusCode <> 'superseded')
UNION ALL
SELECT 'status', StatusCode, COUNT(*) FROM Found GROUP BY StatusCode
UNION ALL
SELECT 'all', '', COUNT(*) FROM dbo.SolutionDecision WHERE SolutionID = @prmSolutionID

GO
