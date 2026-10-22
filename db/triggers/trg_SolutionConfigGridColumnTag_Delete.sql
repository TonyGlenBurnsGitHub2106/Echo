SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.trg_SolutionConfigGridColumnTag_Delete') IS NOT NULL DROP TRIGGER dbo.trg_SolutionConfigGridColumnTag_Delete
GO
/*

Trigger trg_SolutionConfigGridColumnTag_Delete on dbo.SolutionConfigGridColumnTag.

VERSION HISTORY

Date        Developer    Description
----------  -----------  ----------------------------------------------------------
2026-10-09  Felix        Story 5611 Solution Configuration II: audit row into SolutionConfigGridColumnTag_LOG.

*/

CREATE TRIGGER dbo.trg_SolutionConfigGridColumnTag_Delete ON dbo.SolutionConfigGridColumnTag AFTER DELETE AS
BEGIN SET NOCOUNT ON;
    INSERT INTO dbo.SolutionConfigGridColumnTag_LOG (IsInsertion, DeletedDate, SolutionConfigGridColumnTagID, SolutionConfigGridColumnID, SolutionConfigGridAxisID, SolutionConfigGridAxisValueID, CreatedDate)
    SELECT 0, GETDATE(), SolutionConfigGridColumnTagID, SolutionConfigGridColumnID, SolutionConfigGridAxisID, SolutionConfigGridAxisValueID, CreatedDate FROM Deleted; END;

GO
