SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_SolutionNoteStep_FromLog') IS NOT NULL DROP PROCEDURE dbo.stp_SolutionNoteStep_FromLog
GO
CREATE PROCEDURE dbo.stp_SolutionNoteStep_FromLog
    @prmSolutionNoteID INT,
    @prmSolutionLogID      INT
AS

/*

TEST
----

    EXEC stp_SolutionNoteStep_FromLog 1, 1

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------------------------------------
09/17/2026  Felix        Story 3493: promote one journal entry into a runbook step -- the bridge between "how did we do
                         it" and "how do you do it". The entry's first line becomes the step title; a
                         'command' entry lands in the step's command, any other kind in its notes. The step is
                         appended and remembers which entry it came from. Both must belong to the same
                         solution. Returns the id as SolutionNoteStepID.

09/22/2026  Felix        Solutions documents are now NOTES (SolutionDocument* -> SolutionNote*, Tony 09/22/2026).

*/

SET NOCOUNT ON

DECLARE @noteSolution INT = (SELECT SolutionID FROM dbo.SolutionNote WHERE SolutionNoteID = @prmSolutionNoteID)
DECLARE @text NVARCHAR(MAX), @typeCode VARCHAR(20), @logSolution INT
SELECT @text = l.LogText, @typeCode = lt.LogTypeCode, @logSolution = l.SolutionID
FROM   dbo.SolutionLog l JOIN dbo.SolutionLogType lt ON lt.SolutionLogTypeID = l.SolutionLogTypeID
WHERE  l.SolutionLogID = @prmSolutionLogID

IF @noteSolution IS NULL BEGIN RAISERROR('That note no longer exists.', 16, 1) RETURN END
IF @logSolution IS NULL BEGIN RAISERROR('That log entry no longer exists.', 16, 1) RETURN END
IF @noteSolution <> @logSolution BEGIN RAISERROR('The log entry and the note belong to different solutions.', 16, 1) RETURN END

DECLARE @firstLine NVARCHAR(MAX) = @text
DECLARE @nl INT = CHARINDEX(CHAR(10), @text)
IF @nl > 0 SET @firstLine = LEFT(@text, @nl - 1)
SET @firstLine = LTRIM(RTRIM(REPLACE(@firstLine, CHAR(13), '')))
IF @firstLine = '' SET @firstLine = 'Step from log entry'
DECLARE @title NVARCHAR(400) = LEFT(@firstLine, 400)

DECLARE @stepNumber INT = ISNULL((SELECT MAX(StepNumber) FROM dbo.SolutionNoteStep WHERE SolutionNoteID = @prmSolutionNoteID), 0) + 1

INSERT INTO dbo.SolutionNoteStep (SolutionNoteID, StepNumber, StepTitle, StepCommand, StepNotes, SourceSolutionLogID)
VALUES (@prmSolutionNoteID, @stepNumber, @title,
        CASE WHEN @typeCode = 'command' THEN @text ELSE NULL END,
        CASE WHEN @typeCode = 'command' THEN NULL ELSE @text END,
        @prmSolutionLogID)

SELECT CAST(SCOPE_IDENTITY() AS INT) AS SolutionNoteStepID


GO
