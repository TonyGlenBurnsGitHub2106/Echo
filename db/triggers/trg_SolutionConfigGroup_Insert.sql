SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.trg_SolutionConfigGroup_Insert') IS NOT NULL DROP TRIGGER dbo.trg_SolutionConfigGroup_Insert
GO
/*

Trigger trg_SolutionConfigGroup_Insert on dbo.SolutionConfigGroup.

VERSION HISTORY

Date        Developer    Description
----------  -----------  ----------------------------------------------------------
2026-09-21  Felix        Story 3516 Solution configuration: audit row into SolutionConfigGroup_LOG.

*/

CREATE TRIGGER dbo.trg_SolutionConfigGroup_Insert ON dbo.SolutionConfigGroup AFTER INSERT AS
BEGIN SET NOCOUNT ON;
    INSERT INTO dbo.SolutionConfigGroup_LOG (IsInsertion, SolutionConfigGroupID, SolutionID, GroupTitle, SortOrder, CreatedDate, ModifiedDate)
    SELECT 1, SolutionConfigGroupID, SolutionID, GroupTitle, SortOrder, CreatedDate, ModifiedDate FROM Inserted; END;

GO
