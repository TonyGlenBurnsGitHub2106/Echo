SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.trg_SolutionProjectItem_Delete') IS NOT NULL DROP TRIGGER dbo.trg_SolutionProjectItem_Delete
GO
/*

Trigger trg_SolutionProjectItem_Delete on dbo.SolutionProjectItem.

VERSION HISTORY

Date        Developer    Description
----------  -----------  ----------------------------------------------------------
2026-09-17  Felix        Story 3493: audit row into SolutionProjectItem_LOG (the Solutions family).

*/

CREATE TRIGGER dbo.trg_SolutionProjectItem_Delete ON dbo.SolutionProjectItem AFTER DELETE AS
BEGIN SET NOCOUNT ON;
    INSERT INTO dbo.SolutionProjectItem_LOG (IsInsertion, DeletedDate, SolutionProjectItemID, SolutionProjectID, ParentItemID, ItemTitle, ItemDetail, SolutionStatusID, AssigneeMemberID, Priority, SortOrder, DueDate, CompletedDate, CreatedByMemberID, CreatedDate, ModifiedDate, ModifiedBy)
    SELECT 0, GETDATE(), SolutionProjectItemID, SolutionProjectID, ParentItemID, ItemTitle, ItemDetail, SolutionStatusID, AssigneeMemberID, Priority, SortOrder, DueDate, CompletedDate, CreatedByMemberID, CreatedDate, ModifiedDate, ModifiedBy FROM Deleted; END;

GO
