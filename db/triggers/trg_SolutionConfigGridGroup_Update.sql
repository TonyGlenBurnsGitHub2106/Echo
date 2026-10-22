SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.trg_SolutionConfigGridGroup_Update') IS NOT NULL DROP TRIGGER dbo.trg_SolutionConfigGridGroup_Update
GO
/*

Trigger trg_SolutionConfigGridGroup_Update on dbo.SolutionConfigGridGroup.

VERSION HISTORY

Date        Developer    Description
----------  -----------  ----------------------------------------------------------
2026-10-09  Felix        Story 5611 Solution Configuration II: audit row into SolutionConfigGridGroup_LOG.

*/

CREATE TRIGGER dbo.trg_SolutionConfigGridGroup_Update ON dbo.SolutionConfigGridGroup AFTER UPDATE AS
BEGIN SET NOCOUNT ON;
    INSERT INTO dbo.SolutionConfigGridGroup_LOG (IsInsertion, SolutionConfigGridGroupID, SolutionID, GroupTitle, SortOrder, CreatedDate, ModifiedDate)
    SELECT 0, SolutionConfigGridGroupID, SolutionID, GroupTitle, SortOrder, CreatedDate, ModifiedDate FROM Inserted; END;

GO
