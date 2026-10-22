SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.trg_SolutionConfigGroup_Update') IS NOT NULL DROP TRIGGER dbo.trg_SolutionConfigGroup_Update
GO
/*

Trigger trg_SolutionConfigGroup_Update on dbo.SolutionConfigGroup.

VERSION HISTORY

Date        Developer    Description
----------  -----------  ----------------------------------------------------------
2026-09-21  Felix        Story 3516 Solution configuration: audit row into SolutionConfigGroup_LOG.

*/

CREATE TRIGGER dbo.trg_SolutionConfigGroup_Update ON dbo.SolutionConfigGroup AFTER UPDATE AS
BEGIN SET NOCOUNT ON;
    INSERT INTO dbo.SolutionConfigGroup_LOG (IsInsertion, SolutionConfigGroupID, SolutionID, GroupTitle, SortOrder, CreatedDate, ModifiedDate)
    SELECT 0, SolutionConfigGroupID, SolutionID, GroupTitle, SortOrder, CreatedDate, ModifiedDate FROM Inserted; END;

GO
