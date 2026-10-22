SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_SolutionNote_Delete') IS NOT NULL DROP PROCEDURE dbo.stp_SolutionNote_Delete
GO
CREATE PROCEDURE dbo.stp_SolutionNote_Delete
    @prmSolutionNoteID INT
AS

/*

TEST
----

    EXEC stp_SolutionNote_Delete 0

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------------------------------------
09/17/2026  Felix        Story 3493: remove one note with its steps and every saved version. One transaction. Returns RowsAffected.

09/22/2026  Felix        Solutions documents are now NOTES (SolutionDocument* -> SolutionNote*, Tony 09/22/2026).

*/

SET NOCOUNT ON

DECLARE @rows INT = 0
BEGIN TRY
    BEGIN TRANSACTION
    DELETE FROM dbo.SolutionNoteStep    WHERE SolutionNoteID = @prmSolutionNoteID
    DELETE FROM dbo.SolutionNoteVersion WHERE SolutionNoteID = @prmSolutionNoteID
    DELETE FROM dbo.SolutionNote        WHERE SolutionNoteID = @prmSolutionNoteID
    SET @rows = @@ROWCOUNT
    COMMIT TRANSACTION
END TRY
BEGIN CATCH
    IF @@TRANCOUNT > 0 ROLLBACK TRANSACTION
    DECLARE @msg NVARCHAR(2000) = ERROR_MESSAGE()
    RAISERROR(@msg, 16, 1)
    RETURN
END CATCH

SELECT @rows AS RowsAffected


GO
