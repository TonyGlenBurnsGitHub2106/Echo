SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_SolutionFile_CountsFiled') IS NOT NULL DROP PROCEDURE dbo.stp_SolutionFile_CountsFiled
GO
CREATE PROCEDURE dbo.stp_SolutionFile_CountsFiled
    @prmSolutionID INT,
    @prmGroupBy    VARCHAR(20)   = 'category',  -- category | type | year | none
    @prmSearch     NVARCHAR(200) = NULL
AS

/*

Story 5594: the band headers of a solution's organized Files tab -- one row per group under the
same grouping and search stp_SolutionFile_ListFiled uses, in the same order, with how many files
match (Cnt) and how many of those still sit in Staging (StagedCnt). Twin of
stp_EntityFile_CountsFiled. HOUSE-ONLY.

TEST
----

    EXEC dbo.stp_SolutionFile_CountsFiled @prmSolutionID = 143
    EXEC dbo.stp_SolutionFile_CountsFiled @prmSolutionID = 143, @prmGroupBy = 'year'

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------
2026-10-05  Felix        New (Story 5594).

*/

SET NOCOUNT ON

DECLARE @group VARCHAR(20)   = LOWER(ISNULL(@prmGroupBy, 'category'))
DECLARE @q     NVARCHAR(200) = NULLIF(LTRIM(RTRIM(ISNULL(@prmSearch, N''))), N'')
DECLARE @like  NVARCHAR(210) = N'%' + @q + N'%'

;WITH src AS (
    SELECT  CASE @group
                WHEN 'type' THEN 't:' + v.TypeFamily
                WHEN 'year' THEN 'y:' + CAST(YEAR(v.EffectiveDate) AS VARCHAR(4))
                WHEN 'none' THEN 'all'
                ELSE CASE WHEN v.FileCategoryID IS NULL THEN 'staging' ELSE 'c:' + CAST(v.FileCategoryID AS VARCHAR(12)) END
            END AS GroupKey,
            CASE @group
                WHEN 'type' THEN v.TypeFamily
                WHEN 'year' THEN CAST(YEAR(v.EffectiveDate) AS VARCHAR(4))
                WHEN 'none' THEN 'All files'
                ELSE ISNULL(v.CategoryName, 'Staging')
            END AS GroupLabel,
            CASE @group
                WHEN 'type' THEN v.TypeFamilyRank
                WHEN 'year' THEN 10000 - YEAR(v.EffectiveDate)
                WHEN 'none' THEN 0
                ELSE CASE WHEN v.FileCategoryID IS NULL THEN -1 ELSE v.CategorySortOrder END
            END AS GroupRank,
            CASE WHEN @group = 'category' THEN v.CategoryColor ELSE '' END AS GroupColor,
            v.FileCategoryID,
            v.IsStaged
    FROM    dbo.vw_SolutionFileFiled v
    WHERE   v.SolutionID = @prmSolutionID
      AND  (@q IS NULL
            OR v.FileTitle        LIKE @like
            OR v.OriginalFileName LIKE @like
            OR v.CategoryName     LIKE @like
            OR v.ItemTitle        LIKE @like
            OR v.Ext              = @q)
)
SELECT  GroupKey,
        GroupLabel,
        GroupRank,
        MAX(GroupColor)                                     AS GroupColor,
        MAX(FileCategoryID)                                 AS FileCategoryID,
        COUNT(*)                                            AS Cnt,
        SUM(CASE WHEN IsStaged = 1 THEN 1 ELSE 0 END)       AS StagedCnt
FROM    src
GROUP BY GroupKey, GroupLabel, GroupRank
ORDER BY GroupRank, GroupLabel

GO
