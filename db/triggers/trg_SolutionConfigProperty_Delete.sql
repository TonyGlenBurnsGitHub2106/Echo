SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.trg_SolutionConfigProperty_Delete') IS NOT NULL DROP TRIGGER dbo.trg_SolutionConfigProperty_Delete
GO
/*

Trigger trg_SolutionConfigProperty_Delete on dbo.SolutionConfigProperty.

VERSION HISTORY

Date        Developer    Description
----------  -----------  ----------------------------------------------------------
2026-09-21  Felix        Story 3516 Solution configuration: audit row into SolutionConfigProperty_LOG.

*/

CREATE TRIGGER dbo.trg_SolutionConfigProperty_Delete ON dbo.SolutionConfigProperty AFTER DELETE AS
BEGIN SET NOCOUNT ON;
    INSERT INTO dbo.SolutionConfigProperty_LOG (IsInsertion, DeletedDate, SolutionConfigPropertyID, SolutionConfigGroupID, PropertyKey, PropertyDescription, SolutionConfigTypeID, SortOrder, CreatedDate, ModifiedDate, ModifiedBy)
    SELECT 0, GETDATE(), SolutionConfigPropertyID, SolutionConfigGroupID, PropertyKey, PropertyDescription, SolutionConfigTypeID, SortOrder, CreatedDate, ModifiedDate, ModifiedBy FROM Deleted; END;

GO
