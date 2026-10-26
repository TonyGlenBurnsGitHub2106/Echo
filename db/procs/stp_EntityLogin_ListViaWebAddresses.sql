SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_EntityLogin_ListViaWebAddresses') IS NOT NULL DROP PROCEDURE dbo.stp_EntityLogin_ListViaWebAddresses
GO
CREATE PROCEDURE dbo.stp_EntityLogin_ListViaWebAddresses
    @prmEntityID INT
AS

/*

The logins that hang off a record's WEB ADDRESSES, for the "From web addresses"
section of the record's Logins tab (Story 5629).

Path: record -> tblEntityWebAddressXRef -> tblWebAddress (whose own EntityID owns
its logins) -> EntityLoginSetupXRef -> LoginSetup.

One row per (login, web address) pair, ordered by login then web address, so the
service folds consecutive rows into ONE login carrying the list of web addresses
that use it. A login shared by three web addresses therefore shows once.

IsOnRecord = 1 when the same login is also linked straight to the record itself,
so the page can say so instead of leaving the reader to spot the repeat.

TEST
----

    EXEC dbo.stp_EntityLogin_ListViaWebAddresses @prmEntityID = 578

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------------------------------------
2026-10-14  Felix        New proc for the Logins tab's From web addresses section (Story 5629).
2026-10-26  Felix        Story 5653 (Echo): the owning user is an Echo sign-in (AppUser), not tblUser.

*/

SET NOCOUNT ON

-- Distinct (login, web address) pairs first, so a web address linked to the
-- record twice, or a login linked to a web address twice, cannot repeat a row.
;WITH Pairs AS (
    SELECT  DISTINCT lx.LoginSetupID, w.WebAddressID
    FROM    dbo.tblEntityWebAddressXRef wx
    INNER JOIN dbo.tblWebAddress        w  ON w.WebAddressID = wx.WebAddressID
    INNER JOIN dbo.EntityLoginSetupXRef lx ON lx.EntityID    = w.EntityID
    WHERE   wx.EntityID = @prmEntityID
      AND   w.EntityID IS NOT NULL
)
SELECT  l.LoginSetupID,
        ISNULL(l.LoginName,     '')                AS LoginName,
        ISNULL(l.LoginUserName, '')                AS LoginUserName,
        ISNULL(l.Pass,          '')                AS Pass,
        ISNULL(u.Username,      '')                AS OwningUser,
        ISNULL(l.Notes,         '')                AS Notes,
        CONVERT(VARCHAR(10), l.EffectiveDate, 23) AS EffectiveDate,
        CONVERT(VARCHAR(10), l.ThroughDate,   23) AS ThroughDate,
        CAST(CASE WHEN EXISTS (SELECT 1 FROM dbo.EntityLoginSetupXRef d
                               WHERE d.EntityID = @prmEntityID AND d.LoginSetupID = l.LoginSetupID)
                  THEN 1 ELSE 0 END AS BIT)        AS IsOnRecord,
        w.WebAddressID,
        w.EntityID                                 AS WebAddressEntityID,
        ISNULL(w.WebAddressName, '')               AS WebAddressName,
        ISNULL(w.WebAddress,     '')               AS WebAddress,
        w.WebAddressTypeID
FROM    Pairs p
INNER JOIN dbo.LoginSetup    l ON l.LoginSetupID = p.LoginSetupID
INNER JOIN dbo.tblWebAddress w ON w.WebAddressID = p.WebAddressID
LEFT  JOIN dbo.AppUser       u ON u.AppUserID       = l.UserID
ORDER BY l.LoginName, l.LoginSetupID, w.WebAddressName, w.WebAddress, w.WebAddressID

GO
