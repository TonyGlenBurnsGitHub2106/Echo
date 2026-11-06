SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.trg_SolutionProjectItem_Insert') IS NOT NULL DROP TRIGGER dbo.trg_SolutionProjectItem_Insert
GO
/*

Trigger trg_SolutionProjectItem_Insert on dbo.SolutionProjectItem.

VERSION HISTORY

Date        Developer    Description
----------  -----------  ----------------------------------------------------------
2026-09-17  Felix        Story 3493: audit row into SolutionProjectItem_LOG (the Solutions family).
2026-11-06  Felix        Story 5653: carries ReviewedCode, QaCode and AutomatedCode.

*/

CREATE TRIGGER dbo.trg_SolutionProjectItem_Insert ON dbo.SolutionProjectItem AFTER INSERT AS
BEGIN SET NOCOUNT ON;
    INSERT INTO dbo.SolutionProjectItem_LOG (IsInsertion, SolutionProjectItemID, SolutionProjectID, ParentItemID, ItemTitle, ItemDetail, SolutionStatusID, AssigneeMemberID, Priority, SortOrder, DueDate, CompletedDate, CreatedByMemberID, CreatedDate, ModifiedDate, ModifiedBy, ReviewedCode, QaCode, AutomatedCode)
    SELECT 1, SolutionProjectItemID, SolutionProjectID, ParentItemID, ItemTitle, ItemDetail, SolutionStatusID, AssigneeMemberID, Priority, SortOrder, DueDate, CompletedDate, CreatedByMemberID, CreatedDate, ModifiedDate, ModifiedBy, ReviewedCode, QaCode, AutomatedCode FROM Inserted; END;

GO
