SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.trg_Solution_Insert') IS NOT NULL DROP TRIGGER dbo.trg_Solution_Insert
GO
/*

Trigger trg_Solution_Insert on dbo.Solution.

VERSION HISTORY

Date        Developer    Description
----------  -----------  ----------------------------------------------------------
2026-09-17  Felix        Story 3493: audit row into Solution_LOG (the Solutions family).
2026-10-01  Felix        Story 5566: carries EntityID (a solution is now a true entity).

*/

CREATE TRIGGER dbo.trg_Solution_Insert ON dbo.Solution AFTER INSERT AS
BEGIN SET NOCOUNT ON;
    INSERT INTO dbo.Solution_LOG (IsInsertion, SolutionID, SolutionName, SolutionDescription, SolutionStatusID, OwnerMemberID, StatusSummary, TargetDate, IsArchived, SaveFilesToRepo, SolutionAlias, SortOrder, CreatedDate, ModifiedDate, ModifiedBy, EntityID)
    SELECT 1, SolutionID, SolutionName, SolutionDescription, SolutionStatusID, OwnerMemberID, StatusSummary, TargetDate, IsArchived, SaveFilesToRepo, SolutionAlias, SortOrder, CreatedDate, ModifiedDate, ModifiedBy, EntityID FROM Inserted; END;

GO
