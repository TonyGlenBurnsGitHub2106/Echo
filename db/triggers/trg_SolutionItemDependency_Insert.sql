SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.trg_SolutionItemDependency_Insert') IS NOT NULL DROP TRIGGER dbo.trg_SolutionItemDependency_Insert
GO
-- Story 5695 (Echo): keeps dbo.SolutionItemDependency_LOG -- every insert lands there.
CREATE TRIGGER dbo.trg_SolutionItemDependency_Insert ON dbo.SolutionItemDependency AFTER INSERT AS
BEGIN SET NOCOUNT ON;
    INSERT INTO dbo.SolutionItemDependency_LOG (IsInsertion, SolutionItemDependencyID, SolutionProjectItemID, DependsOnSolutionProjectItemID, CreatedByMemberID, CreatedDate)
    SELECT 1, SolutionItemDependencyID, SolutionProjectItemID, DependsOnSolutionProjectItemID, CreatedByMemberID, CreatedDate FROM Inserted; END;

GO
