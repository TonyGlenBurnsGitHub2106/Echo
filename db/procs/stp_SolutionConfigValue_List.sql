SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_SolutionConfigValue_List') IS NOT NULL DROP PROCEDURE dbo.stp_SolutionConfigValue_List
GO
CREATE PROCEDURE dbo.stp_SolutionConfigValue_List
    @prmSolutionID INT
AS

/*

TEST
----

    EXEC stp_SolutionConfigValue_List 6

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------------------------------------
09/21/2026  Felix        Story 3516 Solution configuration: every filled-in cell of one solution: the value, where it is read
                         from, the note, and who last changed it.

*/

SET NOCOUNT ON

SELECT cv.SolutionConfigValueID, cv.SolutionConfigPropertyID, cv.SolutionConfigEnvironmentID, cv.ValueText,
       src.SourceTitle, cv.ValueNote, m.MemberCode AS ModifiedByCode, m.MemberName AS ModifiedByName,
       cv.CreatedDate, cv.ModifiedDate
FROM   dbo.SolutionConfigValue cv
JOIN   dbo.SolutionConfigEnvironment ce ON ce.SolutionConfigEnvironmentID = cv.SolutionConfigEnvironmentID
LEFT JOIN dbo.SolutionConfigSource src  ON src.SolutionConfigSourceID      = cv.SolutionConfigSourceID
LEFT JOIN dbo.SolutionMember m          ON m.SolutionMemberID              = cv.ModifiedByMemberID
WHERE  ce.SolutionID = @prmSolutionID

GO
