SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.trg_Solution_Delete') IS NOT NULL DROP TRIGGER dbo.trg_Solution_Delete
GO
/*

Trigger trg_Solution_Delete on dbo.Solution.

VERSION HISTORY

Date        Developer    Description
----------  -----------  ----------------------------------------------------------
2026-09-17  Felix        Story 3493: audit row into Solution_LOG (the Solutions family).
2026-10-01  Felix        Story 5566: carries EntityID (a solution is now a true entity).

*/

CREATE TRIGGER dbo.trg_Solution_Delete ON dbo.Solution AFTER DELETE AS
BEGIN SET NOCOUNT ON;
    INSERT INTO dbo.Solution_LOG (IsInsertion, DeletedDate, SolutionID, SolutionName, SolutionDescription, SolutionStatusID, OwnerMemberID, StatusSummary, TargetDate, IsArchived, SaveFilesToRepo, SolutionAlias, SortOrder, CreatedDate, ModifiedDate, ModifiedBy, EntityID)
    SELECT 0, GETDATE(), SolutionID, SolutionName, SolutionDescription, SolutionStatusID, OwnerMemberID, StatusSummary, TargetDate, IsArchived, SaveFilesToRepo, SolutionAlias, SortOrder, CreatedDate, ModifiedDate, ModifiedBy, EntityID FROM Deleted; END;

GO
