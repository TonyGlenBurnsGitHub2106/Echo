SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_SolutionMember_List') IS NOT NULL DROP PROCEDURE dbo.stp_SolutionMember_List
GO
CREATE PROCEDURE dbo.stp_SolutionMember_List
    @prmActiveOnly BIT = 0
AS

/*

TEST
----

    EXEC stp_SolutionMember_List

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------------------------------------
09/17/2026  Felix        Story 3493: everyone who can own, be assigned, or author.

*/

SET NOCOUNT ON

SELECT SolutionMemberID, MemberCode, MemberName, IsAgent, IsActive, SortOrder, CreatedDate, ModifiedDate
FROM   dbo.SolutionMember
WHERE  (@prmActiveOnly = 0 OR IsActive = 1)
ORDER BY SortOrder, MemberName

GO
