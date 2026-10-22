SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_SolutionNoteStep_Move') IS NOT NULL DROP PROCEDURE dbo.stp_SolutionNoteStep_Move
GO
CREATE PROCEDURE dbo.stp_SolutionNoteStep_Move
    @prmSolutionNoteStepID INT,
    @prmNewStepNumber          INT
AS

/*

TEST
----

    EXEC stp_SolutionNoteStep_Move 1, 2

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------------------------------------
09/17/2026  Felix        Story 3493: move one step to a new position and renumber the note's steps 1..n around it. Returns RowsAffected.

09/22/2026  Felix        Solutions documents are now NOTES (SolutionDocument* -> SolutionNote*, Tony 09/22/2026).

*/

SET NOCOUNT ON

DECLARE @noteId INT = (SELECT SolutionNoteID FROM dbo.SolutionNoteStep WHERE SolutionNoteStepID = @prmSolutionNoteStepID)
IF @noteId IS NULL BEGIN SELECT 0 AS RowsAffected RETURN END
DECLARE @count INT = (SELECT COUNT(*) FROM dbo.SolutionNoteStep WHERE SolutionNoteID = @noteId)
IF ISNULL(@prmNewStepNumber, 0) <= 0 SET @prmNewStepNumber = 1
IF @prmNewStepNumber > @count SET @prmNewStepNumber = @count

BEGIN TRY
    BEGIN TRANSACTION
    ;WITH Others AS (
        SELECT SolutionNoteStepID, ROW_NUMBER() OVER (ORDER BY StepNumber, SolutionNoteStepID) AS Rn
        FROM   dbo.SolutionNoteStep
        WHERE  SolutionNoteID = @noteId AND SolutionNoteStepID <> @prmSolutionNoteStepID)
    UPDATE s SET s.StepNumber = CASE WHEN o.Rn >= @prmNewStepNumber THEN o.Rn + 1 ELSE o.Rn END
    FROM   dbo.SolutionNoteStep s JOIN Others o ON o.SolutionNoteStepID = s.SolutionNoteStepID
    UPDATE dbo.SolutionNoteStep SET StepNumber = @prmNewStepNumber, ModifiedDate = GETDATE()
    WHERE  SolutionNoteStepID = @prmSolutionNoteStepID
    COMMIT TRANSACTION
END TRY
BEGIN CATCH
    IF @@TRANCOUNT > 0 ROLLBACK TRANSACTION
    DECLARE @msg NVARCHAR(2000) = ERROR_MESSAGE()
    RAISERROR(@msg, 16, 1)
    RETURN
END CATCH

SELECT 1 AS RowsAffected


GO
