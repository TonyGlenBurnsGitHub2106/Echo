SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.trg_SolutionProject_Insert') IS NOT NULL DROP TRIGGER dbo.trg_SolutionProject_Insert
GO
/*

Trigger trg_SolutionProject_Insert on dbo.SolutionProject.

VERSION HISTORY

Date        Developer    Description
----------  -----------  ----------------------------------------------------------
2026-09-17  Felix        Story 3493: audit row into SolutionProject_LOG (the Solutions family).

*/

CREATE TRIGGER dbo.trg_SolutionProject_Insert ON dbo.SolutionProject AFTER INSERT AS
BEGIN SET NOCOUNT ON;
    INSERT INTO dbo.SolutionProject_LOG (IsInsertion, SolutionProjectID, SolutionID, ProjectName, ProjectDescription, SolutionStatusID, OwnerMemberID, SortOrder, CreatedDate, ModifiedDate, ModifiedBy)
    SELECT 1, SolutionProjectID, SolutionID, ProjectName, ProjectDescription, SolutionStatusID, OwnerMemberID, SortOrder, CreatedDate, ModifiedDate, ModifiedBy FROM Inserted; END;

GO
