SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_SolutionProject_Get') IS NOT NULL DROP PROCEDURE dbo.stp_SolutionProject_Get
GO
CREATE PROCEDURE dbo.stp_SolutionProject_Get
    @prmSolutionProjectID INT
AS

/*

TEST
----

    EXEC stp_SolutionProject_Get 1

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------------------------------------
09/17/2026  Felix        Story 3493: one project WITH its full description.

*/

SET NOCOUNT ON

SELECT  p.SolutionProjectID, p.SolutionID, p.ProjectName, p.ProjectDescription,
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
WHERE   p.SolutionProjectID = @prmSolutionProjectID

GO
