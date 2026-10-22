SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_SolutionProjectItem_Get') IS NOT NULL DROP PROCEDURE dbo.stp_SolutionProjectItem_Get
GO
CREATE PROCEDURE dbo.stp_SolutionProjectItem_Get
    @prmSolutionProjectItemID INT
AS

/*

TEST
----

    EXEC stp_SolutionProjectItem_Get 1

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------------------------------------
09/17/2026  Felix        Story 3493: one item WITH its full detail.

*/

SET NOCOUNT ON

SELECT  i.SolutionProjectItemID, i.SolutionProjectID, p.SolutionID, p.ProjectName, i.ParentItemID,
        i.ItemTitle, i.ItemDetail,
        CASE WHEN DATALENGTH(ISNULL(i.ItemDetail, '')) > 0 THEN 1 ELSE 0 END AS HasDetail,
        i.SolutionStatusID, st.StatusCode, st.StatusName, st.IsClosed, st.ColorHex,
        i.AssigneeMemberID, am.MemberCode AS AssigneeCode, am.MemberName AS AssigneeName,
        i.Priority, i.SortOrder, i.DueDate, i.CompletedDate,
        i.CreatedByMemberID, cm.MemberCode AS CreatedByCode, i.CreatedDate, i.ModifiedDate, i.ModifiedBy,
        (SELECT COUNT(*) FROM dbo.SolutionProjectItem c WHERE c.ParentItemID = i.SolutionProjectItemID) AS ChildCount,
        (SELECT COUNT(*) FROM dbo.SolutionLog l WHERE l.SolutionProjectItemID = i.SolutionProjectItemID) AS LogCount
FROM    dbo.SolutionProjectItem i
JOIN    dbo.SolutionProject p  ON p.SolutionProjectID = i.SolutionProjectID
JOIN    dbo.SolutionStatus  st ON st.SolutionStatusID = i.SolutionStatusID
LEFT JOIN dbo.SolutionMember am ON am.SolutionMemberID = i.AssigneeMemberID
LEFT JOIN dbo.SolutionMember cm ON cm.SolutionMemberID = i.CreatedByMemberID
WHERE   i.SolutionProjectItemID = @prmSolutionProjectItemID

GO
