SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_SolutionSavedView_ListForMember') IS NOT NULL DROP PROCEDURE dbo.stp_SolutionSavedView_ListForMember
GO
CREATE PROCEDURE dbo.stp_SolutionSavedView_ListForMember
    @prmSolutionID INT,
    @prmMemberCode VARCHAR(20),
    @prmScopeCode  VARCHAR(20)
AS

/*

TEST
----

    EXEC stp_SolutionSavedView_ListForMember 6, 'tony', 'items'

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------------------------------------
11/04/2026  Felix        Story 5695 (Echo), Release 5: a member's saved views of one tab of a solution, in their order.

*/

SET NOCOUNT ON

SELECT  v.SolutionSavedViewID, v.SolutionID, v.ScopeCode, v.ViewName, v.FilterJson, v.SortOrder, v.CreatedDate, v.ModifiedDate
FROM    dbo.SolutionSavedView v
JOIN    dbo.SolutionMember m ON m.SolutionMemberID = v.SolutionMemberID
WHERE   v.SolutionID = @prmSolutionID AND m.MemberCode = LOWER(LTRIM(RTRIM(ISNULL(@prmMemberCode, ''))))
  AND   v.ScopeCode = LOWER(LTRIM(RTRIM(ISNULL(@prmScopeCode, ''))))
ORDER BY v.SortOrder, v.ViewName

GO
