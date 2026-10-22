SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.trg_SolutionConfigType_Delete') IS NOT NULL DROP TRIGGER dbo.trg_SolutionConfigType_Delete
GO
/*

Trigger trg_SolutionConfigType_Delete on dbo.SolutionConfigType.

VERSION HISTORY

Date        Developer    Description
----------  -----------  ----------------------------------------------------------
2026-09-21  Felix        Story 3516 Solution configuration: audit row into SolutionConfigType_LOG.

*/

CREATE TRIGGER dbo.trg_SolutionConfigType_Delete ON dbo.SolutionConfigType AFTER DELETE AS
BEGIN SET NOCOUNT ON;
    INSERT INTO dbo.SolutionConfigType_LOG (IsInsertion, DeletedDate, SolutionConfigTypeID, TypeCode, TypeName, IsSecret, SortOrder, CreatedDate)
    SELECT 0, GETDATE(), SolutionConfigTypeID, TypeCode, TypeName, IsSecret, SortOrder, CreatedDate FROM Deleted; END;

GO
