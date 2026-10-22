SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.trg_SolutionConfigGridAxis_Delete') IS NOT NULL DROP TRIGGER dbo.trg_SolutionConfigGridAxis_Delete
GO
/*

Trigger trg_SolutionConfigGridAxis_Delete on dbo.SolutionConfigGridAxis.

VERSION HISTORY

Date        Developer    Description
----------  -----------  ----------------------------------------------------------
2026-10-09  Felix        Story 5611 Solution Configuration II: audit row into SolutionConfigGridAxis_LOG.

*/

CREATE TRIGGER dbo.trg_SolutionConfigGridAxis_Delete ON dbo.SolutionConfigGridAxis AFTER DELETE AS
BEGIN SET NOCOUNT ON;
    INSERT INTO dbo.SolutionConfigGridAxis_LOG (IsInsertion, DeletedDate, SolutionConfigGridAxisID, SolutionID, AxisTitle, SortOrder, CreatedDate, ModifiedDate)
    SELECT 0, GETDATE(), SolutionConfigGridAxisID, SolutionID, AxisTitle, SortOrder, CreatedDate, ModifiedDate FROM Deleted; END;

GO
