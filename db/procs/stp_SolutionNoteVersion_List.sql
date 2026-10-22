SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_SolutionNoteVersion_List') IS NOT NULL DROP PROCEDURE dbo.stp_SolutionNoteVersion_List
GO
CREATE PROCEDURE dbo.stp_SolutionNoteVersion_List
    @prmSolutionNoteID INT
AS

/*

TEST
----

    EXEC stp_SolutionNoteVersion_List 1

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------------------------------------
09/17/2026  Felix        Story 3493: the saved versions of one note, newest first -- who, when, why, how long. No bodies.

09/22/2026  Felix        Solutions documents are now NOTES (SolutionDocument* -> SolutionNote*, Tony 09/22/2026).

*/

SET NOCOUNT ON

SELECT v.SolutionNoteVersionID, v.SolutionNoteID, v.VersionNumber, v.ChangeNote,
       am.MemberCode AS AuthorCode, am.MemberName AS AuthorName, v.CreatedDate,
       LEN(ISNULL(v.NoteBody, '')) AS BodyLength,
       CASE WHEN v.VersionNumber = d.CurrentVersionNumber THEN 1 ELSE 0 END AS IsCurrent
FROM   dbo.SolutionNoteVersion v
JOIN   dbo.SolutionNote d ON d.SolutionNoteID = v.SolutionNoteID
LEFT JOIN dbo.SolutionMember am ON am.SolutionMemberID = v.AuthorMemberID
WHERE  v.SolutionNoteID = @prmSolutionNoteID
ORDER BY v.VersionNumber DESC


GO
