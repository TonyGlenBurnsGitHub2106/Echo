SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.trg_SolutionItemDependency_Update') IS NOT NULL DROP TRIGGER dbo.trg_SolutionItemDependency_Update
GO
-- Story 5695 (Echo): keeps dbo.SolutionItemDependency_LOG -- every update lands there.
CREATE TRIGGER dbo.trg_SolutionItemDependency_Update ON dbo.SolutionItemDependency AFTER UPDATE AS
BEGIN SET NOCOUNT ON;
    INSERT INTO dbo.SolutionItemDependency_LOG (IsInsertion, SolutionItemDependencyID, SolutionProjectItemID, DependsOnSolutionProjectItemID, CreatedByMemberID, CreatedDate)
    SELECT 0, SolutionItemDependencyID, SolutionProjectItemID, DependsOnSolutionProjectItemID, CreatedByMemberID, CreatedDate FROM Inserted; END;

GO
