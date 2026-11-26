SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_DailyWorkEntry_Search') IS NOT NULL DROP PROCEDURE dbo.stp_DailyWorkEntry_Search
GO
CREATE PROCEDURE dbo.stp_DailyWorkEntry_Search
    @prmFromDate     DATE          = NULL,   -- on or after (NULL = no lower bound)
    @prmToDate       DATE          = NULL,   -- on or before (NULL = no upper bound)
    @prmText         NVARCHAR(200) = NULL,   -- words that must all appear
    @prmContactID    INT           = NULL,
    @prmTeamID       INT           = NULL,
    @prmFollowUpOnly BIT           = 0,
    @prmTop          INT           = 500
AS

/*

Story 6852 (Daily Work): the entries for the list, newest first, filtered and limited here --
never in Java. Every word of @prmText must appear in what I did, the story, the team, the
kind, a contact's name or a link. Notes are NOT searched and NOT returned (only the first 300
characters, NotesPreview): a list never scans or carries an NVARCHAR(MAX) body. A work log
grows by a handful of rows a day, so LIKE over these short columns stays instant; if it ever
does not, the next step is a token index.

TEST
----

    EXEC dbo.stp_DailyWorkEntry_Search @prmText = N'claims', @prmTop = 20

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------
2026-11-25  Felix        New (Story 6852).

*/

SET NOCOUNT ON

DECLARE @top INT = CASE WHEN ISNULL(@prmTop, 0) BETWEEN 1 AND 2000 THEN @prmTop ELSE 500 END
DECLARE @to  DATETIME = CASE WHEN @prmToDate IS NULL THEN NULL ELSE DATEADD(DAY, 1, CAST(@prmToDate AS DATETIME)) END

-- Split the words here, in NVARCHAR: dbo.Split takes VARCHAR, which turns a letter outside the
-- code page (Cyrillic, Greek, CJK ...) into ? and the search would never find it.
DECLARE @words TABLE (Word NVARCHAR(200))
DECLARE @rest NVARCHAR(200) = LTRIM(RTRIM(LEFT(ISNULL(@prmText, N''), 200))) + N' '
DECLARE @at INT, @one NVARCHAR(200)
WHILE LEN(@rest) > 0
BEGIN
    SET @at = CHARINDEX(N' ', @rest)
    IF @at = 0 SET @at = LEN(@rest) + 1
    SET @one = LTRIM(RTRIM(LEFT(@rest, @at - 1)))
    IF @one <> N'' AND NOT EXISTS (SELECT 1 FROM @words WHERE Word = @one) INSERT INTO @words (Word) VALUES (@one)
    SET @rest = LTRIM(SUBSTRING(@rest, @at + 1, 200))
END

SELECT TOP (@top)
       v.DailyWorkEntryID, v.WorkDate, v.WorkDone, v.StoryRef, v.TeamID, v.TeamName,
       v.DailyWorkKindID, v.KindName, v.MinutesSpent, v.IsFollowUp, v.NotesPreview, v.NotesCut,
       v.ContactList, v.LinkList, v.CreatedBy, v.CreatedDate, v.ModifiedBy, v.ModifiedDate
FROM   dbo.vw_DailyWorkEntry v
WHERE  (@prmFromDate IS NULL OR v.WorkDate >= CAST(@prmFromDate AS DATETIME))
  AND  (@to IS NULL OR v.WorkDate < @to)
  AND  (@prmTeamID IS NULL OR v.TeamID = @prmTeamID)
  AND  (ISNULL(@prmFollowUpOnly, 0) = 0 OR v.IsFollowUp = 1)
  AND  (@prmContactID IS NULL OR EXISTS (SELECT 1 FROM dbo.DailyWorkEntryContact x
                                        WHERE x.DailyWorkEntryID = v.DailyWorkEntryID AND x.ContactID = @prmContactID))
  AND  NOT EXISTS (SELECT 1 FROM @words w
                   WHERE ISNULL(v.WorkDone, N'') + N' ' + ISNULL(v.StoryRef, N'') + N' ' + ISNULL(v.TeamName, N'') + N' '
                         + ISNULL(v.KindName, N'') + N' ' + ISNULL(v.ContactList, N'') + N' ' + ISNULL(v.LinkList, N'')
                         NOT LIKE N'%' + REPLACE(REPLACE(REPLACE(w.Word, N'[', N'[[]'), N'%', N'[%]'), N'_', N'[_]') + N'%')
ORDER BY v.WorkDate DESC, v.DailyWorkEntryID DESC

GO
