SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_Solution_SetSaveFilesToRepo') IS NOT NULL DROP PROCEDURE dbo.stp_Solution_SetSaveFilesToRepo
GO
CREATE PROCEDURE dbo.stp_Solution_SetSaveFilesToRepo
    @prmSolutionID      INT,
    @prmSaveFilesToRepo BIT,
    @prmModifiedBy      VARCHAR(20)
AS

/*

TEST
----

    EXEC stp_Solution_SetSaveFilesToRepo 0, 0, 'felix'

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------------------------------------
09/17/2026  Felix        Story 3493: say where one solution's attachments are saved: 1 = Leonardo Files (git-tracked),
                         0 = the git-ignored 'solutions' nook. A solution that is shared with an outside agent
                         may NOT save to the repo -- what an agent uploads must never be committed -- so turning
                         the switch on while it is shared is refused in words. Files already saved stay where
                         they are; the switch decides where the NEXT one goes. Returns RowsAffected.

*/

SET NOCOUNT ON

IF ISNULL(@prmSaveFilesToRepo, 0) = 1
   AND EXISTS (SELECT 1 FROM dbo.SolutionApiClient WHERE SolutionID = @prmSolutionID) BEGIN
    RAISERROR('This solution is shared with an outside agent, so its files cannot be saved to the repo. Stop sharing it first.', 16, 1)
    RETURN
END

UPDATE dbo.Solution
SET    SaveFilesToRepo = ISNULL(@prmSaveFilesToRepo, 0), ModifiedDate = GETDATE(), ModifiedBy = LOWER(@prmModifiedBy)
WHERE  SolutionID = @prmSolutionID

SELECT @@ROWCOUNT AS RowsAffected

GO
