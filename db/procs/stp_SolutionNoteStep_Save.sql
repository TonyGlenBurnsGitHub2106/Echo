SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_SolutionNoteStep_Save') IS NOT NULL DROP PROCEDURE dbo.stp_SolutionNoteStep_Save
GO
CREATE PROCEDURE dbo.stp_SolutionNoteStep_Save
    @prmSolutionNoteStepID INT,
    @prmSolutionNoteID     INT,
    @prmStepNumber             INT,      -- 0 / NULL on insert = append at the end
    @prmStepTitle              NVARCHAR(400),
    @prmStepCommand            NVARCHAR(MAX),
    @prmExpectedResult         NVARCHAR(MAX),
    @prmStepNotes              NVARCHAR(MAX),
    @prmSourceSolutionLogID    INT
AS

/*

TEST
----

    EXEC stp_SolutionNoteStep_Save 0, 1, 0, 'Stop the service', 'sc stop X', 'STOPPED', NULL, NULL

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------------------------------------
09/17/2026  Felix        Story 3493: insert (id = 0) or update one runbook step. An insert at a given number pushes the
                         later steps down; an update leaves the numbering alone (stp_SolutionNoteStep_Move
                         reorders). Returns the id as SolutionNoteStepID.

09/22/2026  Felix        Solutions documents are now NOTES (SolutionDocument* -> SolutionNote*, Tony 09/22/2026).

*/

SET NOCOUNT ON

SET @prmStepTitle = LTRIM(RTRIM(ISNULL(@prmStepTitle, '')))
IF @prmStepTitle = '' BEGIN RAISERROR('A step needs a title.', 16, 1) RETURN END
IF ISNULL(@prmSourceSolutionLogID, 0) <= 0 SET @prmSourceSolutionLogID = NULL

IF @prmSolutionNoteStepID > 0 BEGIN
    UPDATE dbo.SolutionNoteStep
    SET    StepTitle = @prmStepTitle, StepCommand = @prmStepCommand, ExpectedResult = @prmExpectedResult,
           StepNotes = @prmStepNotes, ModifiedDate = GETDATE()
    WHERE  SolutionNoteStepID = @prmSolutionNoteStepID
END ELSE BEGIN
    IF NOT EXISTS (SELECT 1 FROM dbo.SolutionNote WHERE SolutionNoteID = @prmSolutionNoteID) BEGIN
        RAISERROR('That note no longer exists.', 16, 1) RETURN
    END
    DECLARE @count INT = (SELECT COUNT(*) FROM dbo.SolutionNoteStep WHERE SolutionNoteID = @prmSolutionNoteID)
    IF ISNULL(@prmStepNumber, 0) <= 0 OR @prmStepNumber > @count + 1 SET @prmStepNumber = @count + 1

    BEGIN TRY
        BEGIN TRANSACTION
        UPDATE dbo.SolutionNoteStep SET StepNumber = StepNumber + 1
        WHERE  SolutionNoteID = @prmSolutionNoteID AND StepNumber >= @prmStepNumber
        INSERT INTO dbo.SolutionNoteStep (SolutionNoteID, StepNumber, StepTitle, StepCommand, ExpectedResult, StepNotes, SourceSolutionLogID)
        VALUES (@prmSolutionNoteID, @prmStepNumber, @prmStepTitle, @prmStepCommand, @prmExpectedResult, @prmStepNotes, @prmSourceSolutionLogID)
        SET @prmSolutionNoteStepID = SCOPE_IDENTITY()
        COMMIT TRANSACTION
    END TRY
    BEGIN CATCH
        IF @@TRANCOUNT > 0 ROLLBACK TRANSACTION
        DECLARE @msg NVARCHAR(2000) = ERROR_MESSAGE()
        RAISERROR(@msg, 16, 1)
        RETURN
    END CATCH
END

SELECT @prmSolutionNoteStepID AS SolutionNoteStepID


GO
