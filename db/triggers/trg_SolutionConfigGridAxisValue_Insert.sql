SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.trg_SolutionConfigGridAxisValue_Insert') IS NOT NULL DROP TRIGGER dbo.trg_SolutionConfigGridAxisValue_Insert
GO
/*

Trigger trg_SolutionConfigGridAxisValue_Insert on dbo.SolutionConfigGridAxisValue.

VERSION HISTORY

Date        Developer    Description
----------  -----------  ----------------------------------------------------------
2026-10-09  Felix        Story 5611 Solution Configuration II: audit row into SolutionConfigGridAxisValue_LOG.

*/

CREATE TRIGGER dbo.trg_SolutionConfigGridAxisValue_Insert ON dbo.SolutionConfigGridAxisValue AFTER INSERT AS
BEGIN SET NOCOUNT ON;
    INSERT INTO dbo.SolutionConfigGridAxisValue_LOG (IsInsertion, SolutionConfigGridAxisValueID, SolutionConfigGridAxisID, ValueTitle, ColorHex, SortOrder, CreatedDate, ModifiedDate)
    SELECT 1, SolutionConfigGridAxisValueID, SolutionConfigGridAxisID, ValueTitle, ColorHex, SortOrder, CreatedDate, ModifiedDate FROM Inserted; END;

GO
