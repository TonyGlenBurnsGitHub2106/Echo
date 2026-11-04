SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_SolutionSavedView_Delete') IS NOT NULL DROP PROCEDURE dbo.stp_SolutionSavedView_Delete
GO
CREATE PROCEDURE dbo.stp_SolutionSavedView_Delete
    @prmSolutionSavedViewID INT,
    @prmMemberCode          VARCHAR(20)
AS

/*

TEST
----

    EXEC stp_SolutionSavedView_Delete 4, 'tony'

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------------------------------------
11/04/2026  Felix        Story 5695 (Echo), Release 5: delete one of the member's own saved views (never someone else's). Returns
                         RowsAffected.

*/

SET NOCOUNT ON

DELETE v FROM dbo.SolutionSavedView v
JOIN   dbo.SolutionMember m ON m.SolutionMemberID = v.SolutionMemberID
WHERE  v.SolutionSavedViewID = @prmSolutionSavedViewID AND m.MemberCode = LOWER(LTRIM(RTRIM(ISNULL(@prmMemberCode, ''))))
SELECT @@ROWCOUNT AS RowsAffected

GO
