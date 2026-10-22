SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.trg_SolutionConfigType_Insert') IS NOT NULL DROP TRIGGER dbo.trg_SolutionConfigType_Insert
GO
/*

Trigger trg_SolutionConfigType_Insert on dbo.SolutionConfigType.

VERSION HISTORY

Date        Developer    Description
----------  -----------  ----------------------------------------------------------
2026-09-21  Felix        Story 3516 Solution configuration: audit row into SolutionConfigType_LOG.

*/

CREATE TRIGGER dbo.trg_SolutionConfigType_Insert ON dbo.SolutionConfigType AFTER INSERT AS
BEGIN SET NOCOUNT ON;
    INSERT INTO dbo.SolutionConfigType_LOG (IsInsertion, SolutionConfigTypeID, TypeCode, TypeName, IsSecret, SortOrder, CreatedDate)
    SELECT 1, SolutionConfigTypeID, TypeCode, TypeName, IsSecret, SortOrder, CreatedDate FROM Inserted; END;

GO
