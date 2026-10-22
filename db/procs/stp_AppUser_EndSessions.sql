SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_AppUser_EndSessions') IS NOT NULL DROP PROCEDURE dbo.stp_AppUser_EndSessions
GO
CREATE PROCEDURE dbo.stp_AppUser_EndSessions
    @prmAppUserID INT
AS

/*

TEST
----

    EXEC stp_AppUser_EndSessions 1

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------------------------------------
10/21/2026  Felix        Story 5653 (Echo): sign one person out everywhere -- bumps the session generation every
                         refresh token carries. Returns RowsAffected.

*/

SET NOCOUNT ON

UPDATE dbo.AppUser SET SessionGeneration = SessionGeneration + 1, ModifiedDate = GETDATE() WHERE AppUserID = @prmAppUserID

SELECT @@ROWCOUNT AS RowsAffected

GO
