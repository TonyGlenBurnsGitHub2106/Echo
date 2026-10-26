SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_LW_EntityLogin_List') IS NOT NULL DROP PROCEDURE dbo.stp_LW_EntityLogin_List
GO
CREATE PROCEDURE dbo.stp_LW_EntityLogin_List
    @prmEntityID INT
AS

/*

LeonardoWeb: list LoginSetup rows cross-referenced to a parent entity (contact,
vendor, web-address, etc.) via EntityLoginSetupXRef. Returns clean column names so
the desktop's stp_LoginSetup_List bracket-aliased report column names don't leak.

TEST
----

    EXEC dbo.stp_LW_EntityLogin_List @prmEntityID = 578

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------------------------------------
2026-05-10  Claude       New proc for LeonardoWeb contact / vendor detail Login Setup tab.
2026-10-26  Felix        Story 5653 (Echo): the owning user is an Echo sign-in (AppUser), not tblUser.

*/

SET NOCOUNT ON

SELECT  x.EntityLoginSetupXRefID,
        l.LoginSetupID,
        ISNULL(l.LoginName,     '')                AS LoginName,
        ISNULL(l.LoginUserName, '')                AS LoginUserName,
        ISNULL(l.Pass,          '')                AS Pass,
        ISNULL(u.Username,      '')                AS OwningUser,
        l.UserID,
        ISNULL(l.Notes, '')                        AS Notes,
        CONVERT(VARCHAR(10), l.EffectiveDate, 23) AS EffectiveDate,
        CONVERT(VARCHAR(10), l.ThroughDate,   23) AS ThroughDate
FROM    dbo.EntityLoginSetupXRef x
INNER JOIN dbo.LoginSetup l ON l.LoginSetupID = x.LoginSetupID
LEFT  JOIN dbo.AppUser    u ON u.AppUserID       = l.UserID
WHERE   x.EntityID = @prmEntityID
ORDER BY l.LoginName

GO
