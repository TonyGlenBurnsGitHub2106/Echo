SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.trg_SolutionConfigGroup_Delete') IS NOT NULL DROP TRIGGER dbo.trg_SolutionConfigGroup_Delete
GO
/*

Trigger trg_SolutionConfigGroup_Delete on dbo.SolutionConfigGroup.

VERSION HISTORY

Date        Developer    Description
----------  -----------  ----------------------------------------------------------
2026-09-21  Felix        Story 3516 Solution configuration: audit row into SolutionConfigGroup_LOG.

*/

CREATE TRIGGER dbo.trg_SolutionConfigGroup_Delete ON dbo.SolutionConfigGroup AFTER DELETE AS
BEGIN SET NOCOUNT ON;
    INSERT INTO dbo.SolutionConfigGroup_LOG (IsInsertion, DeletedDate, SolutionConfigGroupID, SolutionID, GroupTitle, SortOrder, CreatedDate, ModifiedDate)
    SELECT 0, GETDATE(), SolutionConfigGroupID, SolutionID, GroupTitle, SortOrder, CreatedDate, ModifiedDate FROM Deleted; END;

GO
