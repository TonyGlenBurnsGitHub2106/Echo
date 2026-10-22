SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_SolutionNoteStep_Delete') IS NOT NULL DROP PROCEDURE dbo.stp_SolutionNoteStep_Delete
GO
CREATE PROCEDURE dbo.stp_SolutionNoteStep_Delete
    @prmSolutionNoteStepID INT
AS

/*

TEST
----

    EXEC stp_SolutionNoteStep_Delete 0

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------------------------------------
09/17/2026  Felix        Story 3493: remove one step and close the gap in the numbering. Returns RowsAffected.

09/22/2026  Felix        Solutions documents are now NOTES (SolutionDocument* -> SolutionNote*, Tony 09/22/2026).

*/

SET NOCOUNT ON

DECLARE @noteId INT = (SELECT SolutionNoteID FROM dbo.SolutionNoteStep WHERE SolutionNoteStepID = @prmSolutionNoteStepID)
DECLARE @rows INT = 0
BEGIN TRY
    BEGIN TRANSACTION
    DELETE FROM dbo.SolutionNoteStep WHERE SolutionNoteStepID = @prmSolutionNoteStepID
    SET @rows = @@ROWCOUNT
    ;WITH Rest AS (
        SELECT SolutionNoteStepID, ROW_NUMBER() OVER (ORDER BY StepNumber, SolutionNoteStepID) AS Rn
        FROM   dbo.SolutionNoteStep WHERE SolutionNoteID = @noteId)
    UPDATE s SET s.StepNumber = r.Rn
    FROM   dbo.SolutionNoteStep s JOIN Rest r ON r.SolutionNoteStepID = s.SolutionNoteStepID
    WHERE  s.StepNumber <> r.Rn
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
