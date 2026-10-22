SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.trg_SolutionConfigValue_Delete') IS NOT NULL DROP TRIGGER dbo.trg_SolutionConfigValue_Delete
GO
/*

Trigger trg_SolutionConfigValue_Delete on dbo.SolutionConfigValue.

VERSION HISTORY

Date        Developer    Description
----------  -----------  ----------------------------------------------------------
2026-09-21  Felix        Story 3516 Solution configuration: audit row into SolutionConfigValue_LOG.

*/

CREATE TRIGGER dbo.trg_SolutionConfigValue_Delete ON dbo.SolutionConfigValue AFTER DELETE AS
BEGIN SET NOCOUNT ON;
    INSERT INTO dbo.SolutionConfigValue_LOG (IsInsertion, DeletedDate, SolutionConfigValueID, SolutionConfigPropertyID, SolutionConfigEnvironmentID, ValueText, SolutionConfigSourceID, ValueNote, ModifiedByMemberID, CreatedDate, ModifiedDate)
    SELECT 0, GETDATE(), SolutionConfigValueID, SolutionConfigPropertyID, SolutionConfigEnvironmentID, ValueText, SolutionConfigSourceID, ValueNote, ModifiedByMemberID, CreatedDate, ModifiedDate FROM Deleted; END;

GO
