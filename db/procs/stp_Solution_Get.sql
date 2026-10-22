SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_Solution_Get') IS NOT NULL DROP PROCEDURE dbo.stp_Solution_Get
GO
CREATE PROCEDURE dbo.stp_Solution_Get
    @prmSolutionID INT
AS

/*

TEST
----

    EXEC stp_Solution_Get 1

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------------------------------------
09/17/2026  Felix        Story 3493: one solution WITH its full description and the same headline numbers the list shows.

09/22/2026  Felix        Solutions documents are now NOTES (SolutionDocument* -> SolutionNote*, Tony 09/22/2026).

*/

SET NOCOUNT ON

SELECT  s.SolutionID, s.SolutionName, s.SolutionDescription,
        s.SolutionStatusID, st.StatusCode, st.StatusName, st.IsClosed, st.ColorHex,
        s.OwnerMemberID, om.MemberCode AS OwnerCode, om.MemberName AS OwnerName,
        s.StatusSummary, s.TargetDate, s.IsArchived, s.SaveFilesToRepo, s.SolutionAlias, s.SortOrder, s.CreatedDate, s.ModifiedDate, s.ModifiedBy,
        (SELECT COUNT(*) FROM dbo.SolutionFile f WHERE f.SolutionID = s.SolutionID)      AS FileCount,
        (SELECT COUNT(*) FROM dbo.SolutionApiClient x WHERE x.SolutionID = s.SolutionID) AS SharedCount,
        (SELECT COUNT(*) FROM dbo.SolutionProject p WHERE p.SolutionID = s.SolutionID) AS ProjectCount,
        ISNULL(ic.ItemCount, 0)    AS ItemCount,
        ISNULL(ic.ClosedCount, 0)  AS ClosedItemCount,
        ISNULL(ic.BlockedCount, 0) AS BlockedItemCount,
        (SELECT COUNT(*) FROM dbo.SolutionLog l WHERE l.SolutionID = s.SolutionID)      AS LogCount,
        (SELECT MAX(l.LogDate) FROM dbo.SolutionLog l WHERE l.SolutionID = s.SolutionID) AS LastLogDate,
        (SELECT COUNT(*) FROM dbo.SolutionNote d WHERE d.SolutionID = s.SolutionID) AS NoteCount,
        (SELECT COUNT(*)
         FROM   dbo.SolutionConversation sc
         JOIN   dbo.ConversationMessage m ON m.ConversationID = sc.ConversationID
         WHERE  sc.SolutionID = s.SolutionID AND m.AwaitingResponseFrom = 'tony') AS AwaitingTonyCount
FROM    dbo.Solution s
JOIN    dbo.SolutionStatus st ON st.SolutionStatusID = s.SolutionStatusID
LEFT JOIN dbo.SolutionMember om ON om.SolutionMemberID = s.OwnerMemberID
LEFT JOIN (
        SELECT p.SolutionID,
               COUNT(*) AS ItemCount,
               SUM(CASE WHEN ist.IsClosed = 1 THEN 1 ELSE 0 END) AS ClosedCount,
               SUM(CASE WHEN ist.StatusCode = 'blocked' THEN 1 ELSE 0 END) AS BlockedCount
        FROM   dbo.SolutionProjectItem i
        JOIN   dbo.SolutionProject p   ON p.SolutionProjectID = i.SolutionProjectID
        JOIN   dbo.SolutionStatus  ist ON ist.SolutionStatusID = i.SolutionStatusID
        GROUP BY p.SolutionID) ic ON ic.SolutionID = s.SolutionID
WHERE   s.SolutionID = @prmSolutionID


GO
