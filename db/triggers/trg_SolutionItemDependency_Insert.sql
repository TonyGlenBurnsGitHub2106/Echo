SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.trg_SolutionItemDependency_Insert') IS NOT NULL DROP TRIGGER dbo.trg_SolutionItemDependency_Insert
GO
/*

Trigger trg_SolutionItemDependency_Insert on dbo.SolutionItemDependency.

VERSION HISTORY

Date        Developer    Description
----------  -----------  ----------------------------------------------------------
2026-11-04  Felix        Story 5695 (Echo) Release 3: audit row into SolutionItemDependency_LOG.

*/

CREATE TRIGGER dbo.trg_SolutionItemDependency_Insert ON dbo.SolutionItemDependency AFTER INSERT AS
BEGIN SET NOCOUNT ON;
    INSERT INTO dbo.SolutionItemDependency_LOG (IsInsertion, SolutionItemDependencyID, SolutionProjectItemID, DependsOnSolutionProjectItemID, CreatedByMemberID, CreatedDate)
    SELECT 1, SolutionItemDependencyID, SolutionProjectItemID, DependsOnSolutionProjectItemID, CreatedByMemberID, CreatedDate FROM Inserted; END;

GO
