SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.trg_SolutionMember_Delete') IS NOT NULL DROP TRIGGER dbo.trg_SolutionMember_Delete
GO
/*

Trigger trg_SolutionMember_Delete on dbo.SolutionMember.

VERSION HISTORY

Date        Developer    Description
----------  -----------  ----------------------------------------------------------
2026-09-17  Felix        Story 3493: audit row into SolutionMember_LOG (the Solutions family).

*/

CREATE TRIGGER dbo.trg_SolutionMember_Delete ON dbo.SolutionMember AFTER DELETE AS
BEGIN SET NOCOUNT ON;
    INSERT INTO dbo.SolutionMember_LOG (IsInsertion, DeletedDate, SolutionMemberID, MemberCode, MemberName, IsAgent, IsActive, SortOrder, CreatedDate, ModifiedDate)
    SELECT 0, GETDATE(), SolutionMemberID, MemberCode, MemberName, IsAgent, IsActive, SortOrder, CreatedDate, ModifiedDate FROM Deleted; END;

GO
