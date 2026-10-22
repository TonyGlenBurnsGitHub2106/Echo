SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_SolutionLog_Get') IS NOT NULL DROP PROCEDURE dbo.stp_SolutionLog_Get
GO
CREATE PROCEDURE dbo.stp_SolutionLog_Get
    @prmSolutionLogID INT
AS

/*

TEST
----

    EXEC stp_SolutionLog_Get 1

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------------------------------------
09/17/2026  Felix        Story 3493: one journal entry WITH its full text.

09/22/2026  Felix        Solutions documents are now NOTES (SolutionDocument* -> SolutionNote*, Tony 09/22/2026).

*/

SET NOCOUNT ON

SELECT  l.SolutionLogID, l.SolutionID, l.SolutionProjectID, p.ProjectName, l.SolutionProjectItemID, i.ItemTitle,
        l.SequenceNumber, l.SolutionLogTypeID, lt.LogTypeCode, lt.LogTypeName,
        l.AuthorMemberID, am.MemberCode AS AuthorCode, am.MemberName AS AuthorName,
        l.LogText, 0 AS IsTruncated, l.LogDate, l.CreatedDate, l.ModifiedDate,
        (SELECT COUNT(*) FROM dbo.SolutionNoteStep s WHERE s.SourceSolutionLogID = l.SolutionLogID) AS StepCount
FROM    dbo.SolutionLog l
JOIN    dbo.SolutionLogType lt ON lt.SolutionLogTypeID = l.SolutionLogTypeID
LEFT JOIN dbo.SolutionMember am ON am.SolutionMemberID = l.AuthorMemberID
LEFT JOIN dbo.SolutionProject p ON p.SolutionProjectID = l.SolutionProjectID
LEFT JOIN dbo.SolutionProjectItem i ON i.SolutionProjectItemID = l.SolutionProjectItemID
WHERE   l.SolutionLogID = @prmSolutionLogID


GO
