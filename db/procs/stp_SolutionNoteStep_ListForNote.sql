SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_SolutionNoteStep_ListForNote') IS NOT NULL DROP PROCEDURE dbo.stp_SolutionNoteStep_ListForNote
GO
CREATE PROCEDURE dbo.stp_SolutionNoteStep_ListForNote
    @prmSolutionNoteID INT
AS

/*

TEST
----

    EXEC stp_SolutionNoteStep_ListForNote 1

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------------------------------------
09/17/2026  Felix        Story 3493: the ordered steps of one runbook. This is the open record, not a list page, so the
                         step text comes back whole.

09/22/2026  Felix        Solutions documents are now NOTES (SolutionDocument* -> SolutionNote*, Tony 09/22/2026).

*/

SET NOCOUNT ON

SELECT s.SolutionNoteStepID, s.SolutionNoteID, s.StepNumber, s.StepTitle, s.StepCommand, s.ExpectedResult,
       s.StepNotes, s.SourceSolutionLogID, l.SequenceNumber AS SourceSequenceNumber, s.CreatedDate, s.ModifiedDate
FROM   dbo.SolutionNoteStep s
LEFT JOIN dbo.SolutionLog l ON l.SolutionLogID = s.SourceSolutionLogID
WHERE  s.SolutionNoteID = @prmSolutionNoteID
ORDER BY s.StepNumber, s.SolutionNoteStepID


GO
