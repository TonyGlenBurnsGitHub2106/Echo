SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_FileCategory_List') IS NOT NULL DROP PROCEDURE dbo.stp_FileCategory_List
GO
CREATE PROCEDURE dbo.stp_FileCategory_List
AS

/*

Story 3520: every file category, in display order, with how many filed documents use it
across all records. A small lookup shown whole (the category chips and the Categories
manager), so there is no paging.

TEST
----

    EXEC dbo.stp_FileCategory_List

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------
2026-09-23  Felix        New (Story 3520).

*/

SET NOCOUNT ON

SELECT  c.FileCategoryID,
        c.CategoryName,
        ISNULL(c.ColorHex, '')  AS ColorHex,
        c.SortOrder,
        ISNULL(u.UseCount, 0)   AS UseCount
FROM    dbo.FileCategory c
        LEFT JOIN (SELECT FileCategoryID, COUNT(*) AS UseCount
                   FROM   dbo.SolutionFileFiling
                   WHERE  FileCategoryID IS NOT NULL
                   GROUP BY FileCategoryID) u ON u.FileCategoryID = c.FileCategoryID
ORDER BY c.SortOrder, c.CategoryName

GO
