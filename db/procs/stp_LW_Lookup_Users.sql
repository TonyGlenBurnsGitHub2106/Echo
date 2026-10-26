SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_LW_Lookup_Users') IS NOT NULL DROP PROCEDURE dbo.stp_LW_Lookup_Users
GO
CREATE PROCEDURE dbo.stp_LW_Lookup_Users
/* VERSION HISTORY
2026-10-26  Felix        Story 5653 (Echo): the users are Echo's active sign-ins (AppUser).
*/
AS
SET NOCOUNT ON
SELECT  AppUserID AS Id,
        Username  AS Name
FROM    dbo.AppUser
WHERE   IsActive = 1
ORDER BY Username

GO
