SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_SolutionProject_Delete') IS NOT NULL DROP PROCEDURE dbo.stp_SolutionProject_Delete
GO
CREATE PROCEDURE dbo.stp_SolutionProject_Delete
    @prmSolutionProjectID INT
AS

/*

TEST
----

    EXEC stp_SolutionProject_Delete 0

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------------------------------------
09/17/2026  Felix        Story 3493: remove one project and its items. The journal and the notes are HISTORY and are
                         kept: their pointer to the project (and to its items) is cleared instead. One
                         transaction. Returns RowsAffected.

09/22/2026  Felix        Solutions documents are now NOTES (SolutionDocument* -> SolutionNote*, Tony 09/22/2026).

*/

SET NOCOUNT ON

DECLARE @rows INT = 0

BEGIN TRY
    BEGIN TRANSACTION

    UPDATE l SET l.SolutionProjectItemID = NULL
    FROM   dbo.SolutionLog l
    JOIN   dbo.SolutionProjectItem i ON i.SolutionProjectItemID = l.SolutionProjectItemID
    WHERE  i.SolutionProjectID = @prmSolutionProjectID
    UPDATE f SET f.SolutionProjectItemID = NULL
    FROM   dbo.SolutionFile f
    JOIN   dbo.SolutionProjectItem i ON i.SolutionProjectItemID = f.SolutionProjectItemID
    WHERE  i.SolutionProjectID = @prmSolutionProjectID
    UPDATE dbo.SolutionLog      SET SolutionProjectID = NULL WHERE SolutionProjectID = @prmSolutionProjectID
    UPDATE dbo.SolutionNote SET SolutionProjectID = NULL WHERE SolutionProjectID = @prmSolutionProjectID
    DELETE FROM dbo.SolutionProjectItem WHERE SolutionProjectID = @prmSolutionProjectID
    DELETE FROM dbo.SolutionProject     WHERE SolutionProjectID = @prmSolutionProjectID
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
