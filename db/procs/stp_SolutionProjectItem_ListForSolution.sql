SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_SolutionProjectItem_ListForSolution') IS NOT NULL DROP PROCEDURE dbo.stp_SolutionProjectItem_ListForSolution
GO
CREATE PROCEDURE dbo.stp_SolutionProjectItem_ListForSolution
    @prmSolutionID        INT,
    @prmSolutionProjectID INT = NULL
AS

/*

TEST
----

    EXEC stp_SolutionProjectItem_ListForSolution 1

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------------------------------------
09/17/2026  Felix        Story 3493: every item of one solution (or of one of its projects) as flat rows carrying
                         ParentItemID; the screen draws the whole tree at once, so this is the one list here
                         that is not paged. Ordered in SQL. DetailPreview is a LEFT(); the full detail comes
                         from stp_SolutionProjectItem_Get.

*/

SET NOCOUNT ON

SELECT  i.SolutionProjectItemID, i.SolutionProjectID, p.SolutionID, p.ProjectName, i.ParentItemID,
        i.ItemTitle, CAST(LEFT(ISNULL(i.ItemDetail, ''), 300) AS NVARCHAR(300)) AS DetailPreview,
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
WHERE   p.SolutionID = @prmSolutionID
AND     (@prmSolutionProjectID IS NULL OR i.SolutionProjectID = @prmSolutionProjectID)
ORDER BY p.SortOrder, p.ProjectName, ISNULL(i.ParentItemID, 0), i.SortOrder, i.SolutionProjectItemID

GO
