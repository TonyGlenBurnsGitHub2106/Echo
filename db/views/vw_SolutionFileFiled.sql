SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.vw_SolutionFileFiled') IS NOT NULL DROP VIEW dbo.vw_SolutionFileFiled
GO
CREATE VIEW dbo.vw_SolutionFileFiled
AS

/*

Story 5594: one row per file attached to a solution, with everything the organized Solutions
Files tab groups, sorts and filters on -- the file, its filing (category, Important, document
date; no filing row = Staging), the category's name, colour and order, the
file extension and its type family. The same type families as vw_EntityLeonardoFileFiled, so a
Type group means the same thing on a record's Files tab and on a solution's.

HOUSE-ONLY. The /api/solutions door (outside agents) never reads this view: a category name is
house information. FileNotes is not projected in full -- a 300-character preview only.

TEST
----

    SELECT TOP 20 * FROM dbo.vw_SolutionFileFiled WHERE SolutionID = 143

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------
2026-10-05  Felix        New (Story 5594).

*/

SELECT  f.SolutionFileID,
        f.SolutionID,
        f.SolutionProjectItemID,
        i.ItemTitle,
        ISNULL(f.FileTitle, '')                     AS FileTitle,
        ISNULL(f.OriginalFileName, '')              AS OriginalFileName,
        f.StorageCode,
        f.LeonardoFileID,
        f.ContentType,
        ISNULL(f.FileSizeBytes, 0)                  AS FileSizeBytes,
        LEFT(ISNULL(f.FileNotes, ''), 300)          AS NotesPreview,
        um.MemberCode                               AS UploadedByCode,
        um.MemberName                               AS UploadedByName,
        f.CreatedDate,
        f.ModifiedDate,
        fl.FileCategoryID,
        c.CategoryName,
        ISNULL(c.ColorHex, '')                      AS CategoryColor,
        ISNULL(c.SortOrder, 0)                      AS CategorySortOrder,
        CAST(CASE WHEN fl.FileCategoryID IS NULL THEN 1 ELSE 0 END AS BIT) AS IsStaged,
        fl.DocumentDate,
        ISNULL(fl.IsImportant, CAST(0 AS BIT))      AS IsImportant,
        fl.FiledDate,
        ISNULL(fl.DocumentDate, CAST(f.CreatedDate AS DATE)) AS EffectiveDate,
        e.Ext,
        CASE WHEN e.Ext = 'PDF'                                                 THEN 'PDF'
             WHEN e.Ext IN ('JPG','JPEG','PNG','GIF','HEIC','WEBP','BMP','TIF','TIFF','SVG') THEN 'Images'
             WHEN e.Ext IN ('DOC','DOCX','RTF','TXT','MD','ODT','LOG')           THEN 'Documents'
             WHEN e.Ext IN ('XLS','XLSX','CSV','ODS')                            THEN 'Spreadsheets'
             WHEN e.Ext IN ('HTML','HTM','MHT')                                  THEN 'Web pages'
             WHEN e.Ext IN ('MP3','M4A','WAV','MP4','MOV','AVI','WEBM')          THEN 'Audio and video'
             ELSE 'Other' END                       AS TypeFamily,
        CASE WHEN e.Ext = 'PDF'                                                 THEN 1
             WHEN e.Ext IN ('JPG','JPEG','PNG','GIF','HEIC','WEBP','BMP','TIF','TIFF','SVG') THEN 2
             WHEN e.Ext IN ('DOC','DOCX','RTF','TXT','MD','ODT','LOG')           THEN 3
             WHEN e.Ext IN ('XLS','XLSX','CSV','ODS')                            THEN 4
             WHEN e.Ext IN ('HTML','HTM','MHT')                                  THEN 5
             WHEN e.Ext IN ('MP3','M4A','WAV','MP4','MOV','AVI','WEBM')          THEN 6
             ELSE 9 END                             AS TypeFamilyRank
FROM    dbo.SolutionFile f
        LEFT JOIN dbo.SolutionProjectItem i  ON i.SolutionProjectItemID = f.SolutionProjectItemID
        LEFT JOIN dbo.SolutionMember um      ON um.SolutionMemberID = f.UploadedByMemberID
        LEFT JOIN dbo.SolutionFileFiling fl  ON fl.SolutionFileID = f.SolutionFileID
        LEFT JOIN dbo.FileCategory c         ON c.FileCategoryID = fl.FileCategoryID
        CROSS APPLY (SELECT ISNULL(f.OriginalFileName, '') AS Leaf) l
        CROSS APPLY (SELECT CASE WHEN CHARINDEX('.', l.Leaf) > 0
                                 THEN UPPER(RIGHT(l.Leaf, CHARINDEX('.', REVERSE(l.Leaf)) - 1))
                                 ELSE '' END AS Ext) e

GO
