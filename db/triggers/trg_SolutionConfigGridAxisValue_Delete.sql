SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.trg_SolutionConfigGridAxisValue_Delete') IS NOT NULL DROP TRIGGER dbo.trg_SolutionConfigGridAxisValue_Delete
GO
/*

Trigger trg_SolutionConfigGridAxisValue_Delete on dbo.SolutionConfigGridAxisValue.

VERSION HISTORY

Date        Developer    Description
----------  -----------  ----------------------------------------------------------
2026-10-09  Felix        Story 5611 Solution Configuration II: audit row into SolutionConfigGridAxisValue_LOG.

*/

CREATE TRIGGER dbo.trg_SolutionConfigGridAxisValue_Delete ON dbo.SolutionConfigGridAxisValue AFTER DELETE AS
BEGIN SET NOCOUNT ON;
    INSERT INTO dbo.SolutionConfigGridAxisValue_LOG (IsInsertion, DeletedDate, SolutionConfigGridAxisValueID, SolutionConfigGridAxisID, ValueTitle, ColorHex, SortOrder, CreatedDate, ModifiedDate)
    SELECT 0, GETDATE(), SolutionConfigGridAxisValueID, SolutionConfigGridAxisID, ValueTitle, ColorHex, SortOrder, CreatedDate, ModifiedDate FROM Deleted; END;

GO
