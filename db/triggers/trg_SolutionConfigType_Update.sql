SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.trg_SolutionConfigType_Update') IS NOT NULL DROP TRIGGER dbo.trg_SolutionConfigType_Update
GO
/*

Trigger trg_SolutionConfigType_Update on dbo.SolutionConfigType.

VERSION HISTORY

Date        Developer    Description
----------  -----------  ----------------------------------------------------------
2026-09-21  Felix        Story 3516 Solution configuration: audit row into SolutionConfigType_LOG.

*/

CREATE TRIGGER dbo.trg_SolutionConfigType_Update ON dbo.SolutionConfigType AFTER UPDATE AS
BEGIN SET NOCOUNT ON;
    INSERT INTO dbo.SolutionConfigType_LOG (IsInsertion, SolutionConfigTypeID, TypeCode, TypeName, IsSecret, SortOrder, CreatedDate)
    SELECT 0, SolutionConfigTypeID, TypeCode, TypeName, IsSecret, SortOrder, CreatedDate FROM Inserted; END;

GO
