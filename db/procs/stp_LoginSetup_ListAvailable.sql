SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_LoginSetup_ListAvailable') IS NOT NULL DROP PROCEDURE dbo.stp_LoginSetup_ListAvailable
GO
CREATE PROCEDURE dbo.stp_LoginSetup_ListAvailable
    @prmEntityID INT
AS

/*

List LoginSetup rows that are NOT already cross-referenced to @prmEntityID, so a
"link an existing login" picker can offer them. Returns the login name, the user
name it signs in with, and the owning Leonardo user so the caller can build a
readable label without re-formatting. Excludes logins already attached to this
entity so the same login cannot be double-linked.

This exists so a login shared by several web addresses / vendors / contacts is
entered ONCE and linked everywhere, instead of being retyped per owner (which
produced three identical "Parent Square" logins before this shipped).

Mirrors stp_LW_WebAddress_Available.

TEST
----

    EXEC dbo.stp_LoginSetup_ListAvailable @prmEntityID = 98141

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------------------------------------
2026-08-06  Felix        New proc for the link-an-existing-login picker.
2026-10-26  Felix        Story 5653 (Echo): the owning user is an Echo sign-in (AppUser), not tblUser.

*/

SET NOCOUNT ON

SELECT  l.LoginSetupID,
        ISNULL(l.LoginName,     '')               AS LoginName,
        ISNULL(l.LoginUserName, '')               AS LoginUserName,
        ISNULL(u.Username,      '')               AS OwningUser,
        CONVERT(VARCHAR(10), l.EffectiveDate, 23) AS EffectiveDate,
        CONVERT(VARCHAR(10), l.ThroughDate,   23) AS ThroughDate
FROM    dbo.LoginSetup l
LEFT  JOIN dbo.AppUser u ON u.AppUserID = l.UserID
WHERE   NOT EXISTS (SELECT 1 FROM dbo.EntityLoginSetupXRef x
                    WHERE x.LoginSetupID = l.LoginSetupID AND x.EntityID = @prmEntityID)
ORDER BY l.LoginName, l.LoginUserName

GO
