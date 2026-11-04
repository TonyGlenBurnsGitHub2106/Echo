SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_SolutionMemberVisit_GetNote') IS NOT NULL DROP PROCEDURE dbo.stp_SolutionMemberVisit_GetNote
GO
CREATE PROCEDURE dbo.stp_SolutionMemberVisit_GetNote
    @prmSolutionID INT,
    @prmMemberCode VARCHAR(20)
AS

/*

TEST
----

    EXEC stp_SolutionMemberVisit_GetNote 6, 'tony'

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------------------------------------
11/04/2026  Felix        Story 5695 (Echo): a member's note for next time on one solution, and when it was written.
                         No row when there is none.

*/

SET NOCOUNT ON

SELECT  v.PartingNote, v.PartingNoteDate
FROM    dbo.SolutionMemberVisit v
JOIN    dbo.SolutionMember m ON m.SolutionMemberID = v.SolutionMemberID
WHERE   v.SolutionID = @prmSolutionID AND m.MemberCode = LOWER(LTRIM(RTRIM(ISNULL(@prmMemberCode, ''))))
  AND   v.PartingNote IS NOT NULL

GO
