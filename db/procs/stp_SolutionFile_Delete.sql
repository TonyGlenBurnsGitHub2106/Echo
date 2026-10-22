SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_SolutionFile_Delete') IS NOT NULL DROP PROCEDURE dbo.stp_SolutionFile_Delete
GO
CREATE PROCEDURE dbo.stp_SolutionFile_Delete
    @prmSolutionFileID INT
AS

/*

TEST
----

    EXEC stp_SolutionFile_Delete 0

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------------------------------------
09/17/2026  Felix        Story 3493: detach one file from its solution. Removes the ROW only -- the bytes are never
                         unlinked here (a nook file is moved to the Reading Nook trash by the service; a
                         Leonardo Files copy stays in the catalog). Returns RowsAffected.
10/05/2026  Felix        Story 5594: also removes the file's filing row (SolutionFileFiling), in one transaction.

*/

SET NOCOUNT ON

SET XACT_ABORT ON

DECLARE @rows INT

BEGIN TRAN
    DELETE FROM dbo.SolutionFileFiling WHERE SolutionFileID = @prmSolutionFileID
    DELETE FROM dbo.SolutionFile WHERE SolutionFileID = @prmSolutionFileID
    SET @rows = @@ROWCOUNT
COMMIT

SELECT @rows AS RowsAffected

GO
