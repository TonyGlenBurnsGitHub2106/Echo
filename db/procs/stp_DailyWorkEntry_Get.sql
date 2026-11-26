SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_DailyWorkEntry_Get') IS NOT NULL DROP PROCEDURE dbo.stp_DailyWorkEntry_Get
GO
CREATE PROCEDURE dbo.stp_DailyWorkEntry_Get
    @prmDailyWorkEntryID INT
AS

/*

Story 6852 (Daily Work): one entry with its full notes, contacts and links (for View and Edit).
No row = not found.

TEST
----

    EXEC dbo.stp_DailyWorkEntry_Get @prmDailyWorkEntryID = 1

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------
2026-11-25  Felix        New (Story 6852).

*/

SET NOCOUNT ON

SELECT v.DailyWorkEntryID, v.WorkDate, v.WorkDone, v.StoryRef, v.TeamID, v.TeamName,
       v.DailyWorkKindID, v.KindName, v.MinutesSpent, v.IsFollowUp, v.Notes, v.NotesPreview, v.NotesCut,
       v.ContactList, v.LinkList, v.CreatedBy, v.CreatedDate, v.ModifiedBy, v.ModifiedDate
FROM   dbo.vw_DailyWorkEntry v
WHERE  v.DailyWorkEntryID = @prmDailyWorkEntryID

GO
