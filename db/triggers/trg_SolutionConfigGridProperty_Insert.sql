SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.trg_SolutionConfigGridProperty_Insert') IS NOT NULL DROP TRIGGER dbo.trg_SolutionConfigGridProperty_Insert
GO
/*

Trigger trg_SolutionConfigGridProperty_Insert on dbo.SolutionConfigGridProperty.

VERSION HISTORY

Date        Developer    Description
----------  -----------  ----------------------------------------------------------
2026-10-09  Felix        Story 5611 Solution Configuration II: audit row into SolutionConfigGridProperty_LOG.

*/

CREATE TRIGGER dbo.trg_SolutionConfigGridProperty_Insert ON dbo.SolutionConfigGridProperty AFTER INSERT AS
BEGIN SET NOCOUNT ON;
    INSERT INTO dbo.SolutionConfigGridProperty_LOG (IsInsertion, SolutionConfigGridPropertyID, SolutionID, SolutionConfigGridGroupID, PropertyKey, PropertyDescription, SolutionConfigTypeID, SortOrder, CreatedDate, ModifiedDate, ModifiedBy)
    SELECT 1, SolutionConfigGridPropertyID, SolutionID, SolutionConfigGridGroupID, PropertyKey, PropertyDescription, SolutionConfigTypeID, SortOrder, CreatedDate, ModifiedDate, ModifiedBy FROM Inserted; END;

GO
