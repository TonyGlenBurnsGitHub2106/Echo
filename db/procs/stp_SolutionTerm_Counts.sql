SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_SolutionTerm_Counts') IS NOT NULL DROP PROCEDURE dbo.stp_SolutionTerm_Counts
GO
CREATE PROCEDURE dbo.stp_SolutionTerm_Counts
    @prmSolutionID    INT,
    @prmSearch        NVARCHAR(200) = NULL,
    @prmTypeID        INT           = NULL,
    @prmCategoryID    INT           = NULL,
    @prmFavoritesOnly BIT           = 0,
    @prmGroupBy       VARCHAR(20)   = 'type'
AS

/*

TEST
----

    EXEC stp_SolutionTerm_Counts 6
    EXEC stp_SolutionTerm_Counts 6, N'api', NULL, NULL, 0, 'category'

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------------------------------------
10/09/2026  Felix        Solution Terms tab: the counts behind the tab, as Bucket / BucketKey / Cnt rows --
                           total      terms matching the filter
                           group      per group of the filtered list (BucketKey = the GroupKey the list proc gives)
                           all        every term of the solution, unfiltered
                           favorites  favorites of the solution, unfiltered
                           types      types the solution has
                           categories categories the solution has
                         The filter is the SAME as stp_SolutionTerm_ListForSolution's, so a count always describes
                         the rows the list returns.

*/

SET NOCOUNT ON

DECLARE @solutionId INT         = @prmSolutionID
DECLARE @search     NVARCHAR(200) = NULLIF(LTRIM(RTRIM(ISNULL(@prmSearch, N''))), N'')
DECLARE @typeId     INT         = NULLIF(@prmTypeID, 0)
DECLARE @categoryId INT         = NULLIF(@prmCategoryID, 0)
DECLARE @fav        BIT         = ISNULL(@prmFavoritesOnly, 0)
DECLARE @groupBy    VARCHAR(20) = CASE WHEN @prmGroupBy IN ('type', 'category', 'none') THEN @prmGroupBy ELSE 'type' END
DECLARE @like       NVARCHAR(420) = CASE WHEN @search IS NULL THEN NULL
                                    ELSE N'%' + REPLACE(REPLACE(REPLACE(@search, N'[', N'[[]'), N'%', N'[%]'), N'_', N'[_]') + N'%' END

DECLARE @f TABLE (SolutionTermID INT PRIMARY KEY, SolutionTermTypeID INT NULL)

INSERT INTO @f (SolutionTermID, SolutionTermTypeID)
SELECT t.SolutionTermID, t.SolutionTermTypeID
FROM   dbo.SolutionTerm t
LEFT JOIN dbo.SolutionTermType ty ON ty.SolutionTermTypeID = t.SolutionTermTypeID
WHERE  t.SolutionID = @solutionId
  AND  (@typeId IS NULL OR t.SolutionTermTypeID = @typeId)
  AND  (@fav = 0 OR t.IsFavorite = 1)
  AND  (@categoryId IS NULL OR EXISTS (SELECT 1 FROM dbo.SolutionTermCategoryXRef x
                                       WHERE x.SolutionTermID = t.SolutionTermID AND x.SolutionTermCategoryID = @categoryId))
  AND  (@like IS NULL OR t.Term LIKE @like OR t.Definition LIKE @like OR t.TermSource LIKE @like
        OR t.TermNotes LIKE @like OR ty.TypeName LIKE @like
        OR EXISTS (SELECT 1 FROM dbo.SolutionTermCategoryXRef x
                   JOIN dbo.SolutionTermCategory c ON c.SolutionTermCategoryID = x.SolutionTermCategoryID
                   WHERE x.SolutionTermID = t.SolutionTermID AND c.CategoryName LIKE @like))

SELECT 'total' AS Bucket, '' AS BucketKey, COUNT(*) AS Cnt FROM @f
UNION ALL
SELECT 'group', 'type:' + CONVERT(VARCHAR(12), ISNULL(SolutionTermTypeID, 0)), COUNT(*)
FROM   @f WHERE @groupBy = 'type'
GROUP BY ISNULL(SolutionTermTypeID, 0)
UNION ALL
SELECT 'group', 'category:' + CONVERT(VARCHAR(12), ISNULL(x.SolutionTermCategoryID, 0)), COUNT(*)
FROM   @f f
LEFT JOIN dbo.SolutionTermCategoryXRef x ON x.SolutionTermID = f.SolutionTermID
WHERE  @groupBy = 'category'
GROUP BY ISNULL(x.SolutionTermCategoryID, 0)
UNION ALL
SELECT 'all', '', COUNT(*) FROM dbo.SolutionTerm WHERE SolutionID = @solutionId
UNION ALL
SELECT 'favorites', '', COUNT(*) FROM dbo.SolutionTerm WHERE SolutionID = @solutionId AND IsFavorite = 1
UNION ALL
SELECT 'types', '', COUNT(*) FROM dbo.SolutionTermType WHERE SolutionID = @solutionId
UNION ALL
SELECT 'categories', '', COUNT(*) FROM dbo.SolutionTermCategory WHERE SolutionID = @solutionId

GO
