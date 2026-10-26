SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.vw_EntityWebAddressFiled') IS NOT NULL DROP VIEW dbo.vw_EntityWebAddressFiled
GO
CREATE VIEW dbo.vw_EntityWebAddressFiled
AS

/*

Organized Web tab (Story 3525): one row per web address on a record (tblEntityWebAddressXRef)
with what the Web tab groups, sorts and filters on -- the address, its kind (website / email
address / FTP, tblWebAddressType), its website type with the type's colour and place in the
order (dbo.WebSiteTypeStyle; unstyled types sort after the ordered ones), and its Important
flag (dbo.EntityWebAddressFlag), and Tony's own place for it on the record
(dbo.EntityWebAddressSequence; unplaced addresses sort after the placed ones). Notes come whole (varchar 5000, so the edit dialog can
show them all) plus a 300-character, single-line preview for the card.

TEST
----

    SELECT TOP 20 * FROM dbo.vw_EntityWebAddressFiled WHERE EntityID = 41

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------
2026-09-24  Felix        New (Story 3525).
2026-10-04  Felix        ItemSortOrder -- the address's place inside its group.

*/

SELECT  x.EntityWebAddressXRefID,
        x.EntityID,
        w.WebAddressID,
        w.EntityID                                                      AS WebAddressEntityID,
        ISNULL(w.WebAddressName, '')                                    AS WebAddressName,
        ISNULL(w.WebAddress, '')                                        AS WebAddress,
        w.WebAddressTypeID,
        ISNULL(k.WebAddressType, '')                                    AS WebAddressType,
        w.WebSiteTypeID,
        ISNULL(t.WebSiteType, '')                                       AS WebSiteType,
        ISNULL(s.ColorHex, '')                                          AS TypeColor,
        ISNULL(s.SortOrder, 1000000)                                    AS TypeSortOrder,
        ISNULL(w.Notes, '')                                             AS Notes,
        LEFT(REPLACE(REPLACE(ISNULL(w.Notes, ''), CHAR(13), ' '), CHAR(10), ' '), 300)
                                                                        AS NotesPreview,
        CAST(ISNULL(f.IsImportant, 0) AS BIT)                           AS IsImportant,
        x.CreatedDate                                                   AS AssociatedDate,
        ISNULL(q.SortOrder, 1000000)                                    AS ItemSortOrder
FROM    dbo.tblEntityWebAddressXRef x
        INNER JOIN dbo.tblWebAddress w          ON w.WebAddressID = x.WebAddressID
        LEFT  JOIN dbo.tblWebAddressType k      ON k.WebAddressTypeID = w.WebAddressTypeID
        LEFT  JOIN dbo.WebSiteType t            ON t.WebSiteTypeID = w.WebSiteTypeID
        LEFT  JOIN dbo.WebSiteTypeStyle s       ON s.WebSiteTypeID = w.WebSiteTypeID
        LEFT  JOIN dbo.EntityWebAddressFlag f   ON f.EntityWebAddressXRefID = x.EntityWebAddressXRefID
        LEFT  JOIN dbo.EntityWebAddressSequence q ON q.EntityWebAddressXRefID = x.EntityWebAddressXRefID

GO
