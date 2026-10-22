SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.trg_SolutionConfigGridGroup_Insert') IS NOT NULL DROP TRIGGER dbo.trg_SolutionConfigGridGroup_Insert
GO
/*

Trigger trg_SolutionConfigGridGroup_Insert on dbo.SolutionConfigGridGroup.

VERSION HISTORY

Date        Developer    Description
----------  -----------  ----------------------------------------------------------
2026-10-09  Felix        Story 5611 Solution Configuration II: audit row into SolutionConfigGridGroup_LOG.

*/

CREATE TRIGGER dbo.trg_SolutionConfigGridGroup_Insert ON dbo.SolutionConfigGridGroup AFTER INSERT AS
BEGIN SET NOCOUNT ON;
    INSERT INTO dbo.SolutionConfigGridGroup_LOG (IsInsertion, SolutionConfigGridGroupID, SolutionID, GroupTitle, SortOrder, CreatedDate, ModifiedDate)
    SELECT 1, SolutionConfigGridGroupID, SolutionID, GroupTitle, SortOrder, CreatedDate, ModifiedDate FROM Inserted; END;

GO
