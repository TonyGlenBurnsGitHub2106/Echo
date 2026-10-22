SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.trg_SolutionConfigValue_Insert') IS NOT NULL DROP TRIGGER dbo.trg_SolutionConfigValue_Insert
GO
/*

Trigger trg_SolutionConfigValue_Insert on dbo.SolutionConfigValue.

VERSION HISTORY

Date        Developer    Description
----------  -----------  ----------------------------------------------------------
2026-09-21  Felix        Story 3516 Solution configuration: audit row into SolutionConfigValue_LOG.

*/

CREATE TRIGGER dbo.trg_SolutionConfigValue_Insert ON dbo.SolutionConfigValue AFTER INSERT AS
BEGIN SET NOCOUNT ON;
    INSERT INTO dbo.SolutionConfigValue_LOG (IsInsertion, SolutionConfigValueID, SolutionConfigPropertyID, SolutionConfigEnvironmentID, ValueText, SolutionConfigSourceID, ValueNote, ModifiedByMemberID, CreatedDate, ModifiedDate)
    SELECT 1, SolutionConfigValueID, SolutionConfigPropertyID, SolutionConfigEnvironmentID, ValueText, SolutionConfigSourceID, ValueNote, ModifiedByMemberID, CreatedDate, ModifiedDate FROM Inserted; END;

GO
