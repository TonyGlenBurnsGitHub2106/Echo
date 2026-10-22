SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_FileCategory_Delete') IS NOT NULL DROP PROCEDURE dbo.stp_FileCategory_Delete
GO
CREATE PROCEDURE dbo.stp_FileCategory_Delete
    @prmFileCategoryID INT
AS

/*

Story 3520: delete one file category. Nothing it held is lost -- every document filed
under it goes back to STAGING on its record, to be filed again. Returns FilesReturned,
the number of documents sent back, so the page can say so.

TEST
----

    BEGIN TRAN; EXEC dbo.stp_FileCategory_Delete @prmFileCategoryID = 8; ROLLBACK

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------
2026-09-23  Felix        New (Story 3520).

*/

SET NOCOUNT ON
SET XACT_ABORT ON

DECLARE @returned INT = 0

BEGIN TRAN

    UPDATE dbo.SolutionFileFiling
    SET    FileCategoryID = NULL,
           FiledDate      = NULL,
           ModifiedDate   = GETDATE()
    WHERE  FileCategoryID = @prmFileCategoryID
    SET @returned = @@ROWCOUNT

    DELETE FROM dbo.FileCategory WHERE FileCategoryID = @prmFileCategoryID

COMMIT

SELECT @returned AS FilesReturned

GO
