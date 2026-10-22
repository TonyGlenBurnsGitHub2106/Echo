SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.trg_SolutionMember_Update') IS NOT NULL DROP TRIGGER dbo.trg_SolutionMember_Update
GO
/*

Trigger trg_SolutionMember_Update on dbo.SolutionMember.

VERSION HISTORY

Date        Developer    Description
----------  -----------  ----------------------------------------------------------
2026-09-17  Felix        Story 3493: audit row into SolutionMember_LOG (the Solutions family).

*/

CREATE TRIGGER dbo.trg_SolutionMember_Update ON dbo.SolutionMember AFTER UPDATE AS
BEGIN SET NOCOUNT ON;
    INSERT INTO dbo.SolutionMember_LOG (IsInsertion, SolutionMemberID, MemberCode, MemberName, IsAgent, IsActive, SortOrder, CreatedDate, ModifiedDate)
    SELECT 0, SolutionMemberID, MemberCode, MemberName, IsAgent, IsActive, SortOrder, CreatedDate, ModifiedDate FROM Inserted; END;

GO
