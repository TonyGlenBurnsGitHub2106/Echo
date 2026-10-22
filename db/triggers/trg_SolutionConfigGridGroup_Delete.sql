SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.trg_SolutionConfigGridGroup_Delete') IS NOT NULL DROP TRIGGER dbo.trg_SolutionConfigGridGroup_Delete
GO
/*

Trigger trg_SolutionConfigGridGroup_Delete on dbo.SolutionConfigGridGroup.

VERSION HISTORY

Date        Developer    Description
----------  -----------  ----------------------------------------------------------
2026-10-09  Felix        Story 5611 Solution Configuration II: audit row into SolutionConfigGridGroup_LOG.

*/

CREATE TRIGGER dbo.trg_SolutionConfigGridGroup_Delete ON dbo.SolutionConfigGridGroup AFTER DELETE AS
BEGIN SET NOCOUNT ON;
    INSERT INTO dbo.SolutionConfigGridGroup_LOG (IsInsertion, DeletedDate, SolutionConfigGridGroupID, SolutionID, GroupTitle, SortOrder, CreatedDate, ModifiedDate)
    SELECT 0, GETDATE(), SolutionConfigGridGroupID, SolutionID, GroupTitle, SortOrder, CreatedDate, ModifiedDate FROM Deleted; END;

GO
