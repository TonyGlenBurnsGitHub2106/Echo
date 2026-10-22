SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_SolutionTerm_ListForSolution') IS NOT NULL DROP PROCEDURE dbo.stp_SolutionTerm_ListForSolution
GO
CREATE PROCEDURE dbo.stp_SolutionTerm_ListForSolution
    @prmSolutionID    INT,
    @prmSearch        NVARCHAR(200) = NULL,    -- term, definition, source, notes, type or category
    @prmTypeID        INT           = NULL,
    @prmCategoryID    INT           = NULL,
    @prmFavoritesOnly BIT           = 0,
    @prmGroupBy       VARCHAR(20)   = 'type',  -- type | category | none
    @prmReverse       BIT           = 0,
    @prmOffset        INT           = 0,
    @prmPageSize      INT           = 50
AS

/*

TEST
----

    EXEC stp_SolutionTerm_ListForSolution 6
    EXEC stp_SolutionTerm_ListForSolution 6, N'api', NULL, NULL, 0, 'category', 0, 0, 50

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------------------------------------
10/09/2026  Felix        Solution Terms tab: one page of a solution's glossary, filtered, grouped and ordered here so
                         page 2 continues page 1. Grouped by type (a term once), by category (a term once per
                         category it carries; none = "(Uncategorized)") or not at all. Named groups first, the
                         catch-all group last; Reverse flips the group order (and the term order when ungrouped).
                         The definition is a 600-character preview (IsTruncated says when there is more);
                         stp_SolutionTerm_Get reads the whole term. CategoryList is the term's categories, one per
                         line. stp_SolutionTerm_Counts counts the same filter.

*/

SET NOCOUNT ON

-- Copied into locals so the plan is not compiled for one sniffed filter (leonardo-programming skill).
DECLARE @solutionId INT         = @prmSolutionID
DECLARE @search     NVARCHAR(200) = NULLIF(LTRIM(RTRIM(ISNULL(@prmSearch, N''))), N'')
DECLARE @typeId     INT         = NULLIF(@prmTypeID, 0)
DECLARE @categoryId INT         = NULLIF(@prmCategoryID, 0)
DECLARE @fav        BIT         = ISNULL(@prmFavoritesOnly, 0)
DECLARE @groupBy    VARCHAR(20) = CASE WHEN @prmGroupBy IN ('type', 'category', 'none') THEN @prmGroupBy ELSE 'type' END
DECLARE @reverse    BIT         = ISNULL(@prmReverse, 0)
DECLARE @offset     INT         = CASE WHEN ISNULL(@prmOffset, 0) < 0 THEN 0 ELSE ISNULL(@prmOffset, 0) END
DECLARE @size       INT         = CASE WHEN ISNULL(@prmPageSize, 0) <= 0 THEN 50 WHEN @prmPageSize > 500 THEN 500 ELSE @prmPageSize END
DECLARE @like       NVARCHAR(420) = CASE WHEN @search IS NULL THEN NULL
                                    ELSE N'%' + REPLACE(REPLACE(REPLACE(@search, N'[', N'[[]'), N'%', N'[%]'), N'_', N'[_]') + N'%' END

;WITH f AS (
    SELECT t.SolutionTermID, t.Term, t.SolutionTermTypeID, ty.TypeName
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
), g AS (
    SELECT f.SolutionTermID, f.Term,
           CASE WHEN @groupBy = 'none' THEN '' ELSE 'type:' + CONVERT(VARCHAR(12), ISNULL(f.SolutionTermTypeID, 0)) END AS GroupKey,
           CASE WHEN @groupBy = 'none' THEN N'' ELSE ISNULL(f.TypeName, N'(No type)') END AS GroupLabel,
           CASE WHEN @groupBy <> 'none' AND f.SolutionTermTypeID IS NULL THEN 1 ELSE 0 END AS GroupRank
    FROM   f
    WHERE  @groupBy <> 'category'
    UNION ALL
    SELECT f.SolutionTermID, f.Term,
           'category:' + CONVERT(VARCHAR(12), ISNULL(c.SolutionTermCategoryID, 0)),
           ISNULL(c.CategoryName, N'(Uncategorized)'),
           CASE WHEN c.SolutionTermCategoryID IS NULL THEN 1 ELSE 0 END
    FROM   f
    LEFT JOIN dbo.SolutionTermCategoryXRef x ON x.SolutionTermID = f.SolutionTermID
    LEFT JOIN dbo.SolutionTermCategory c ON c.SolutionTermCategoryID = x.SolutionTermCategoryID
    WHERE  @groupBy = 'category'
)
SELECT g.GroupKey, g.GroupLabel,
       t.SolutionTermID, t.SolutionID, t.Term,
       CAST(LEFT(t.Definition, 600) AS NVARCHAR(600)) AS Definition,
       CAST(CASE WHEN LEN(t.Definition) > 600 THEN 1 ELSE 0 END AS BIT) AS IsTruncated,
       t.SolutionTermTypeID, ty.TypeName, t.TermSource,
       CAST(CASE WHEN LEN(ISNULL(t.TermNotes, N'')) > 0 THEN 1 ELSE 0 END AS BIT) AS HasNotes,
       t.IsFavorite,
       ISNULL((SELECT NCHAR(10) + c2.CategoryName
               FROM   dbo.SolutionTermCategoryXRef x2
               JOIN   dbo.SolutionTermCategory c2 ON c2.SolutionTermCategoryID = x2.SolutionTermCategoryID
               WHERE  x2.SolutionTermID = t.SolutionTermID
               ORDER BY c2.CategoryName
               FOR XML PATH(''), TYPE).value('.', 'NVARCHAR(MAX)'), N'') AS CategoryList,
       a.MemberCode AS AuthorCode, a.MemberName AS AuthorName,
       t.CreatedDate, t.ModifiedDate
FROM   g
JOIN   dbo.SolutionTerm t ON t.SolutionTermID = g.SolutionTermID
LEFT JOIN dbo.SolutionTermType ty ON ty.SolutionTermTypeID = t.SolutionTermTypeID
LEFT JOIN dbo.SolutionMember a ON a.SolutionMemberID = t.AuthorMemberID
ORDER BY g.GroupRank,
         CASE WHEN @reverse = 0 THEN g.GroupLabel END,
         CASE WHEN @reverse = 1 THEN g.GroupLabel END DESC,
         CASE WHEN @reverse = 1 AND @groupBy = 'none' THEN t.Term END DESC,
         t.Term, t.SolutionTermID
OFFSET @offset ROWS FETCH NEXT @size ROWS ONLY

GO
