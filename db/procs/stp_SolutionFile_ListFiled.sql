SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_SolutionFile_ListFiled') IS NOT NULL DROP PROCEDURE dbo.stp_SolutionFile_ListFiled
GO
CREATE PROCEDURE dbo.stp_SolutionFile_ListFiled
    @prmSolutionID INT,
    @prmGroupBy    VARCHAR(20)   = 'category',  -- category | type | year | none
    @prmSortKey    VARCHAR(20)   = 'added',     -- added | name | docdate | size | type
    @prmSortDesc   BIT           = 1,
    @prmSearch     NVARCHAR(200) = NULL,
    @prmOffset     INT           = 0,
    @prmPageSize   INT           = 50
AS

/*

Story 5594: one page of the organized Files tab of one SOLUTION -- the same grouping, sorting and
search as a record's Files tab (stp_EntityFile_ListFiled), so the two tabs cannot be told apart.
Rows come back ordered by group (Staging first when grouping by category), then Important files
first, then the chosen sort, so page 2 continues page 1. Each row carries GroupKey / GroupLabel /
GroupColor so the page draws its bands without deciding anything itself. @prmSearch matches the
title, the file name, the category, the linked item or the extension.

HOUSE-ONLY: called from /api/solution-files, never from the /api/solutions agent door.

TEST
----

    EXEC dbo.stp_SolutionFile_ListFiled @prmSolutionID = 143
    EXEC dbo.stp_SolutionFile_ListFiled @prmSolutionID = 143, @prmGroupBy = 'type', @prmSortKey = 'name', @prmSortDesc = 0

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------
2026-10-05  Felix        New (Story 5594).

*/

SET NOCOUNT ON

DECLARE @group  VARCHAR(20)   = LOWER(ISNULL(@prmGroupBy, 'category'))
DECLARE @sort   VARCHAR(20)   = LOWER(ISNULL(@prmSortKey, 'added'))
DECLARE @desc   BIT           = ISNULL(@prmSortDesc, 1)
DECLARE @q      NVARCHAR(200) = NULLIF(LTRIM(RTRIM(ISNULL(@prmSearch, N''))), N'')
DECLARE @like   NVARCHAR(210) = N'%' + @q + N'%'
DECLARE @offset INT           = CASE WHEN ISNULL(@prmOffset, 0) < 0 THEN 0 ELSE ISNULL(@prmOffset, 0) END
DECLARE @size   INT           = CASE WHEN ISNULL(@prmPageSize, 0) BETWEEN 1 AND 500 THEN @prmPageSize ELSE 50 END

;WITH src AS (
    SELECT  v.*,
            CASE @group
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
            CASE WHEN @group = 'category' THEN v.CategoryColor ELSE '' END AS GroupColor
    FROM    dbo.vw_SolutionFileFiled v
    WHERE   v.SolutionID = @prmSolutionID
      AND  (@q IS NULL
            OR v.FileTitle        LIKE @like
            OR v.OriginalFileName LIKE @like
            OR v.CategoryName     LIKE @like
            OR v.ItemTitle        LIKE @like
            OR v.Ext              = @q)
)
SELECT  SolutionFileID, SolutionID, SolutionProjectItemID, ItemTitle, FileTitle, OriginalFileName,
        StorageCode, LeonardoFileID, ContentType, FileSizeBytes, NotesPreview,
        UploadedByCode, UploadedByName, CreatedDate, ModifiedDate,
        FileCategoryID, CategoryName, CategoryColor, IsStaged, DocumentDate, IsImportant, FiledDate,
        EffectiveDate, Ext, TypeFamily,
        GroupKey, GroupLabel, GroupRank, GroupColor
FROM    src
ORDER BY GroupRank, GroupLabel,
        IsImportant DESC,
        CASE WHEN @sort = 'name'    AND @desc = 0 THEN FileTitle END ASC,
        CASE WHEN @sort = 'name'    AND @desc = 1 THEN FileTitle END DESC,
        CASE WHEN @sort = 'docdate' AND @desc = 0 THEN EffectiveDate END ASC,
        CASE WHEN @sort = 'docdate' AND @desc = 1 THEN EffectiveDate END DESC,
        CASE WHEN @sort = 'size'    AND @desc = 0 THEN FileSizeBytes END ASC,
        CASE WHEN @sort = 'size'    AND @desc = 1 THEN FileSizeBytes END DESC,
        CASE WHEN @sort = 'type'    AND @desc = 0 THEN Ext END ASC,
        CASE WHEN @sort = 'type'    AND @desc = 1 THEN Ext END DESC,
        CASE WHEN @desc = 0 THEN CreatedDate END ASC,
        CASE WHEN @desc = 1 THEN CreatedDate END DESC,
        FileTitle,
        SolutionFileID DESC
OFFSET @offset ROWS FETCH NEXT @size ROWS ONLY

GO
