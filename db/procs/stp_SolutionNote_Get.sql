SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_SolutionNote_Get') IS NOT NULL DROP PROCEDURE dbo.stp_SolutionNote_Get
GO
CREATE PROCEDURE dbo.stp_SolutionNote_Get
    @prmSolutionNoteID INT
AS

/*

TEST
----

    EXEC stp_SolutionNote_Get 1

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------------------------------------
09/17/2026  Felix        Story 3493: one note WITH the body of its current version.

09/22/2026  Felix        Solutions documents are now NOTES (SolutionDocument* -> SolutionNote*, Tony 09/22/2026).

*/

SET NOCOUNT ON

SELECT  d.SolutionNoteID, d.SolutionID, d.SolutionProjectID, p.ProjectName,
        d.SolutionNoteTypeID, dt.NoteTypeCode, dt.NoteTypeName,
        d.NoteTitle, d.CurrentVersionNumber, d.SortOrder, d.CreatedDate, d.ModifiedDate, d.ModifiedBy,
        am.MemberCode AS AuthorCode, am.MemberName AS AuthorName, v.CreatedDate AS VersionDate, v.ChangeNote,
        (SELECT COUNT(*) FROM dbo.SolutionNoteStep s WHERE s.SolutionNoteID = d.SolutionNoteID) AS StepCount,
        v.NoteBody
FROM    dbo.SolutionNote d
JOIN    dbo.SolutionNoteType dt ON dt.SolutionNoteTypeID = d.SolutionNoteTypeID
LEFT JOIN dbo.SolutionProject p ON p.SolutionProjectID = d.SolutionProjectID
LEFT JOIN dbo.SolutionNoteVersion v ON v.SolutionNoteID = d.SolutionNoteID AND v.VersionNumber = d.CurrentVersionNumber
LEFT JOIN dbo.SolutionMember am ON am.SolutionMemberID = v.AuthorMemberID
WHERE   d.SolutionNoteID = @prmSolutionNoteID


GO
