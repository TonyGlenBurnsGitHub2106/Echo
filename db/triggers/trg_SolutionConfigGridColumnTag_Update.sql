SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.trg_SolutionConfigGridColumnTag_Update') IS NOT NULL DROP TRIGGER dbo.trg_SolutionConfigGridColumnTag_Update
GO
/*

Trigger trg_SolutionConfigGridColumnTag_Update on dbo.SolutionConfigGridColumnTag.

VERSION HISTORY

Date        Developer    Description
----------  -----------  ----------------------------------------------------------
2026-10-09  Felix        Story 5611 Solution Configuration II: audit row into SolutionConfigGridColumnTag_LOG.

*/

CREATE TRIGGER dbo.trg_SolutionConfigGridColumnTag_Update ON dbo.SolutionConfigGridColumnTag AFTER UPDATE AS
BEGIN SET NOCOUNT ON;
    INSERT INTO dbo.SolutionConfigGridColumnTag_LOG (IsInsertion, SolutionConfigGridColumnTagID, SolutionConfigGridColumnID, SolutionConfigGridAxisID, SolutionConfigGridAxisValueID, CreatedDate)
    SELECT 0, SolutionConfigGridColumnTagID, SolutionConfigGridColumnID, SolutionConfigGridAxisID, SolutionConfigGridAxisValueID, CreatedDate FROM Inserted; END;

GO
