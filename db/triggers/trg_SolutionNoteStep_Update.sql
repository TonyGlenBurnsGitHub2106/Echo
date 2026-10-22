SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.trg_SolutionNoteStep_Update') IS NOT NULL DROP TRIGGER dbo.trg_SolutionNoteStep_Update
GO
/*

Trigger trg_SolutionNoteStep_Update on dbo.SolutionNoteStep.

VERSION HISTORY

Date        Developer    Description
----------  -----------  ----------------------------------------------------------
2026-09-17  Felix        Story 3493: audit row into SolutionNoteStep_LOG (the Solutions family).

2026-09-22  Felix        Solutions documents are now NOTES (SolutionDocument* -> SolutionNote*, Tony 09/22/2026).

*/

CREATE TRIGGER dbo.trg_SolutionNoteStep_Update ON dbo.SolutionNoteStep AFTER UPDATE AS
BEGIN SET NOCOUNT ON;
    INSERT INTO dbo.SolutionNoteStep_LOG (IsInsertion, SolutionNoteStepID, SolutionNoteID, StepNumber, StepTitle, StepCommand, ExpectedResult, StepNotes, SourceSolutionLogID, CreatedDate, ModifiedDate)
    SELECT 0, SolutionNoteStepID, SolutionNoteID, StepNumber, StepTitle, StepCommand, ExpectedResult, StepNotes, SourceSolutionLogID, CreatedDate, ModifiedDate FROM Inserted; END;

GO
