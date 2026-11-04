SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_SolutionFileVersion_Get') IS NOT NULL DROP PROCEDURE dbo.stp_SolutionFileVersion_Get
GO
CREATE PROCEDURE dbo.stp_SolutionFileVersion_Get
    @prmSolutionFileVersionID INT
AS

/*

TEST
----

    EXEC stp_SolutionFileVersion_Get 3

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------------------------------------
11/04/2026  Felix        Story 5695 (Echo), Release 5: one earlier version of a file, with the solution it belongs to (for the access check).

*/

SET NOCOUNT ON

SELECT  v.SolutionFileVersionID, v.SolutionFileID, v.VersionNumber, v.OriginalFileName, v.StoredFileName, v.ContentType,
        v.FileSizeBytes, v.ChangeNote, v.UploadedDate, m.MemberCode AS UploadedByCode, m.MemberName AS UploadedByName,
        f.SolutionID
FROM    dbo.SolutionFileVersion v
JOIN    dbo.SolutionFile f ON f.SolutionFileID = v.SolutionFileID
LEFT JOIN dbo.SolutionMember m ON m.SolutionMemberID = v.UploadedByMemberID
WHERE   v.SolutionFileVersionID = @prmSolutionFileVersionID

GO
