SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_SolutionProject_ListForSolution') IS NOT NULL DROP PROCEDURE dbo.stp_SolutionProject_ListForSolution
GO
CREATE PROCEDURE dbo.stp_SolutionProject_ListForSolution
    @prmSolutionID INT
AS

/*

TEST
----

    EXEC stp_SolutionProject_ListForSolution 1

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------------------------------------
09/17/2026  Felix        Story 3493: the projects (workstreams) of one solution with their item counts, counted in SQL.
                         DescriptionPreview is a LEFT(); the full description comes from stp_SolutionProject_Get.

*/

SET NOCOUNT ON

SELECT  p.SolutionProjectID, p.SolutionID, p.ProjectName, CAST(LEFT(ISNULL(p.ProjectDescription, ''), 300) AS NVARCHAR(300)) AS DescriptionPreview,
        p.SolutionStatusID, st.StatusCode, st.StatusName, st.IsClosed, st.ColorHex,
        p.OwnerMemberID, om.MemberCode AS OwnerCode, om.MemberName AS OwnerName,
        p.SortOrder, p.CreatedDate, p.ModifiedDate, p.ModifiedBy,
        ISNULL(ic.ItemCount, 0) AS ItemCount, ISNULL(ic.ClosedCount, 0) AS ClosedItemCount,
        ISNULL(ic.BlockedCount, 0) AS BlockedItemCount
FROM    dbo.SolutionProject p
JOIN    dbo.SolutionStatus st ON st.SolutionStatusID = p.SolutionStatusID
LEFT JOIN dbo.SolutionMember om ON om.SolutionMemberID = p.OwnerMemberID
LEFT JOIN (
        SELECT i.SolutionProjectID,
               COUNT(*) AS ItemCount,
               SUM(CASE WHEN ist.IsClosed = 1 THEN 1 ELSE 0 END) AS ClosedCount,
               SUM(CASE WHEN ist.StatusCode = 'blocked' THEN 1 ELSE 0 END) AS BlockedCount
        FROM   dbo.SolutionProjectItem i
        JOIN   dbo.SolutionStatus ist ON ist.SolutionStatusID = i.SolutionStatusID
        GROUP BY i.SolutionProjectID) ic ON ic.SolutionProjectID = p.SolutionProjectID
WHERE   p.SolutionID = @prmSolutionID
ORDER BY p.SortOrder, p.ProjectName

GO
