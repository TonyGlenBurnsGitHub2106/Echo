SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.vw_DailyWorkEntry') IS NOT NULL DROP VIEW dbo.vw_DailyWorkEntry
GO
/*

View vw_DailyWorkEntry (Story 6852, Daily Work): one row per entry with its team and kind
names, and its contacts and links folded into one column each, so a list is one query.

  ContactList  "id:name" items separated by a TAB, in the order they were picked (a tab is legal
               in FOR XML; the save procs keep tabs out of names and links)
  LinkList     the URLs separated by a TAB, in the order they were typed
  Notes        the full notes -- list procs must NOT return it (they read NotesPreview)

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------
2026-11-25  Felix        New (Story 6852).

*/
CREATE VIEW dbo.vw_DailyWorkEntry
AS
SELECT e.DailyWorkEntryID,
       e.WorkDate,
       e.WorkDone,
       e.StoryRef,
       e.TeamID,
       t.TeamName,
       e.DailyWorkKindID,
       k.KindName,
       e.MinutesSpent,
       e.IsFollowUp,
       e.Notes,
       CAST(LEFT(e.Notes, 300) AS NVARCHAR(300)) AS NotesPreview,
       CAST(CASE WHEN LEN(ISNULL(e.Notes, N'')) > 300 THEN 1 ELSE 0 END AS BIT) AS NotesCut,
       e.CreatedBy,
       e.CreatedDate,
       e.ModifiedBy,
       e.ModifiedDate,
       STUFF((SELECT NCHAR(9) + CAST(c.ContactID AS NVARCHAR(12)) + N':' + c.ContactName
              FROM   dbo.DailyWorkEntryContact x
              JOIN   dbo.Contact c ON c.ContactID = x.ContactID
              WHERE  x.DailyWorkEntryID = e.DailyWorkEntryID
              ORDER BY x.SortOrder, x.DailyWorkEntryContactID
              FOR XML PATH(''), TYPE).value('.', 'NVARCHAR(MAX)'), 1, 1, N'') AS ContactList,
       STUFF((SELECT NCHAR(9) + l.LinkUrl
              FROM   dbo.DailyWorkEntryLink l
              WHERE  l.DailyWorkEntryID = e.DailyWorkEntryID
              ORDER BY l.SortOrder, l.DailyWorkEntryLinkID
              FOR XML PATH(''), TYPE).value('.', 'NVARCHAR(MAX)'), 1, 1, N'') AS LinkList
FROM   dbo.DailyWorkEntry e
LEFT JOIN dbo.Team t          ON t.TeamID = e.TeamID
LEFT JOIN dbo.DailyWorkKind k ON k.DailyWorkKindID = e.DailyWorkKindID

GO
