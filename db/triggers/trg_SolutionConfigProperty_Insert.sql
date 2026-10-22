SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.trg_SolutionConfigProperty_Insert') IS NOT NULL DROP TRIGGER dbo.trg_SolutionConfigProperty_Insert
GO
/*

Trigger trg_SolutionConfigProperty_Insert on dbo.SolutionConfigProperty.

VERSION HISTORY

Date        Developer    Description
----------  -----------  ----------------------------------------------------------
2026-09-21  Felix        Story 3516 Solution configuration: audit row into SolutionConfigProperty_LOG.

*/

CREATE TRIGGER dbo.trg_SolutionConfigProperty_Insert ON dbo.SolutionConfigProperty AFTER INSERT AS
BEGIN SET NOCOUNT ON;
    INSERT INTO dbo.SolutionConfigProperty_LOG (IsInsertion, SolutionConfigPropertyID, SolutionConfigGroupID, PropertyKey, PropertyDescription, SolutionConfigTypeID, SortOrder, CreatedDate, ModifiedDate, ModifiedBy)
    SELECT 1, SolutionConfigPropertyID, SolutionConfigGroupID, PropertyKey, PropertyDescription, SolutionConfigTypeID, SortOrder, CreatedDate, ModifiedDate, ModifiedBy FROM Inserted; END;

GO
