SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.trg_SolutionConfigGridProperty_Delete') IS NOT NULL DROP TRIGGER dbo.trg_SolutionConfigGridProperty_Delete
GO
/*

Trigger trg_SolutionConfigGridProperty_Delete on dbo.SolutionConfigGridProperty.

VERSION HISTORY

Date        Developer    Description
----------  -----------  ----------------------------------------------------------
2026-10-09  Felix        Story 5611 Solution Configuration II: audit row into SolutionConfigGridProperty_LOG.

*/

CREATE TRIGGER dbo.trg_SolutionConfigGridProperty_Delete ON dbo.SolutionConfigGridProperty AFTER DELETE AS
BEGIN SET NOCOUNT ON;
    INSERT INTO dbo.SolutionConfigGridProperty_LOG (IsInsertion, DeletedDate, SolutionConfigGridPropertyID, SolutionID, SolutionConfigGridGroupID, PropertyKey, PropertyDescription, SolutionConfigTypeID, SortOrder, CreatedDate, ModifiedDate, ModifiedBy)
    SELECT 0, GETDATE(), SolutionConfigGridPropertyID, SolutionID, SolutionConfigGridGroupID, PropertyKey, PropertyDescription, SolutionConfigTypeID, SortOrder, CreatedDate, ModifiedDate, ModifiedBy FROM Deleted; END;

GO
