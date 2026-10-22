SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.trg_SolutionMember_Insert') IS NOT NULL DROP TRIGGER dbo.trg_SolutionMember_Insert
GO
/*

Trigger trg_SolutionMember_Insert on dbo.SolutionMember.

VERSION HISTORY

Date        Developer    Description
----------  -----------  ----------------------------------------------------------
2026-09-17  Felix        Story 3493: audit row into SolutionMember_LOG (the Solutions family).

*/

CREATE TRIGGER dbo.trg_SolutionMember_Insert ON dbo.SolutionMember AFTER INSERT AS
BEGIN SET NOCOUNT ON;
    INSERT INTO dbo.SolutionMember_LOG (IsInsertion, SolutionMemberID, MemberCode, MemberName, IsAgent, IsActive, SortOrder, CreatedDate, ModifiedDate)
    SELECT 1, SolutionMemberID, MemberCode, MemberName, IsAgent, IsActive, SortOrder, CreatedDate, ModifiedDate FROM Inserted; END;

GO
