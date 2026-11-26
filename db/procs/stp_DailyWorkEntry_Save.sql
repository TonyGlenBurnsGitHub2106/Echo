SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_DailyWorkEntry_Save') IS NOT NULL DROP PROCEDURE dbo.stp_DailyWorkEntry_Save
GO
CREATE PROCEDURE dbo.stp_DailyWorkEntry_Save
    @prmDailyWorkEntryID INT,                     -- 0 = a new entry
    @prmWorkDate         DATETIME       = NULL,   -- NULL = now
    @prmWorkDone         NVARCHAR(1000),
    @prmStoryRef         NVARCHAR(400)  = NULL,
    @prmTeamID           INT            = NULL,
    @prmDailyWorkKindID  INT            = NULL,
    @prmMinutesSpent     INT            = NULL,
    @prmIsFollowUp       BIT            = 0,
    @prmNotes            NVARCHAR(MAX)  = NULL,
    @prmContactIDs       VARCHAR(4000)  = NULL,   -- "12,7,30" in the order picked
    @prmLinks            NVARCHAR(MAX)  = NULL,   -- one URL per line
    @prmUser             NVARCHAR(100)  = NULL
AS

/*

Story 6852 (Daily Work): add or change one entry, its contacts and its links, in one
transaction. Only @prmWorkDone is required.

Everything is checked BEFORE the first write; a refusal ends "Nothing was saved." and writes
nothing (SaveRefusedException shows it as is):
  - what I did is blank
  - the entry, team, kind or a contact is not in Echo
  - minutes outside 0 .. 1440
  - a link that does not start with http:// or https:// (no javascript: in an href)
  - a new entry with the same words as one saved in the last two minutes (a double tap)

Contacts and links are replaced as a set. A contact with no team yet is given the entry's
team, so the page can fill the team from the contact next time.
Returns DailyWorkEntryID.

TEST
----

    BEGIN TRAN
    EXEC dbo.stp_DailyWorkEntry_Save @prmDailyWorkEntryID = 0, @prmWorkDone = N'Test entry',
         @prmLinks = N'https://example.com/a', @prmUser = N'tony'
    ROLLBACK

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------
2026-11-25  Felix        New (Story 6852).

*/

SET NOCOUNT ON
SET XACT_ABORT ON

DECLARE @workDone NVARCHAR(1000) = LTRIM(RTRIM(ISNULL(@prmWorkDone, N'')))
DECLARE @story    NVARCHAR(400)  = NULLIF(LTRIM(RTRIM(ISNULL(@prmStoryRef, N''))), N'')
DECLARE @notes    NVARCHAR(MAX)  = NULLIF(LTRIM(RTRIM(ISNULL(@prmNotes, N''))), N'')
DECLARE @when     DATETIME       = ISNULL(@prmWorkDate, GETDATE())
DECLARE @id       INT            = ISNULL(@prmDailyWorkEntryID, 0)

-- ---- check everything first ------------------------------------------------------------

IF @workDone = N''
BEGIN
    RAISERROR('Say what you did. Nothing was saved.', 16, 1)
    RETURN
END
IF @id > 0 AND NOT EXISTS (SELECT 1 FROM dbo.DailyWorkEntry WHERE DailyWorkEntryID = @id)
BEGIN
    RAISERROR('That entry is no longer in Echo. Nothing was saved.', 16, 1)
    RETURN
END
IF @prmTeamID IS NOT NULL AND NOT EXISTS (SELECT 1 FROM dbo.Team WHERE TeamID = @prmTeamID)
BEGIN
    RAISERROR('That team is not in Echo. Nothing was saved.', 16, 1)
    RETURN
END
IF @prmDailyWorkKindID IS NOT NULL AND NOT EXISTS (SELECT 1 FROM dbo.DailyWorkKind WHERE DailyWorkKindID = @prmDailyWorkKindID)
BEGIN
    RAISERROR('That kind of work is not in Echo. Nothing was saved.', 16, 1)
    RETURN
END
IF @prmMinutesSpent IS NOT NULL AND (@prmMinutesSpent < 0 OR @prmMinutesSpent > 1440)
BEGIN
    RAISERROR('Minutes spent should be between 0 and 1440. Nothing was saved.', 16, 1)
    RETURN
END

DECLARE @contacts TABLE (Seq INT IDENTITY(1,1), ContactID INT)
INSERT INTO @contacts (ContactID)
SELECT CAST(LTRIM(RTRIM(items)) AS INT)
FROM   dbo.Split(ISNULL(@prmContactIDs, ''), ',')
WHERE  LTRIM(RTRIM(items)) <> '' AND LTRIM(RTRIM(items)) NOT LIKE '%[^0-9]%' AND LEN(LTRIM(RTRIM(items))) <= 9

IF EXISTS (SELECT 1 FROM @contacts x WHERE NOT EXISTS (SELECT 1 FROM dbo.Contact c WHERE c.ContactID = x.ContactID))
BEGIN
    RAISERROR('A contact on this entry is not in Echo. Nothing was saved.', 16, 1)
    RETURN
END

-- One URL per line; blank lines dropped; tabs never kept (the list separator in the view).
DECLARE @links TABLE (Seq INT IDENTITY(1,1), LinkUrl NVARCHAR(1000))
DECLARE @rest NVARCHAR(MAX) = REPLACE(REPLACE(ISNULL(@prmLinks, N''), NCHAR(13), N''), NCHAR(9), N'')
DECLARE @at INT, @one NVARCHAR(MAX)
WHILE LEN(@rest) > 0
BEGIN
    SET @at = CHARINDEX(NCHAR(10), @rest)
    IF @at = 0 BEGIN SET @one = @rest; SET @rest = N'' END
    ELSE BEGIN SET @one = LEFT(@rest, @at - 1); SET @rest = SUBSTRING(@rest, @at + 1, LEN(@rest)) END
    SET @one = LTRIM(RTRIM(@one))
    IF @one <> N''
    BEGIN
        IF LEN(@one) > 1000
        BEGIN
            RAISERROR('A link is longer than 1000 characters. Nothing was saved.', 16, 1)
            RETURN
        END
        IF @one NOT LIKE N'http://%' AND @one NOT LIKE N'https://%'
        BEGIN
            RAISERROR('A link should start with http:// or https://. Nothing was saved.', 16, 1)
            RETURN
        END
        INSERT INTO @links (LinkUrl) VALUES (@one)
    END
END

IF @id = 0 AND EXISTS (SELECT 1 FROM dbo.DailyWorkEntry
                       WHERE WorkDone = @workDone AND CreatedDate >= DATEADD(MINUTE, -2, GETDATE()))
BEGIN
    RAISERROR('That entry was just saved. Nothing was saved.', 16, 1)
    RETURN
END

-- ---- write -----------------------------------------------------------------------------

BEGIN TRAN

    IF @id = 0
    BEGIN
        INSERT INTO dbo.DailyWorkEntry (WorkDate, WorkDone, StoryRef, TeamID, DailyWorkKindID, MinutesSpent,
                                        IsFollowUp, Notes, CreatedBy)
        VALUES (@when, @workDone, @story, @prmTeamID, @prmDailyWorkKindID, @prmMinutesSpent,
                ISNULL(@prmIsFollowUp, 0), @notes, @prmUser)
        SET @id = SCOPE_IDENTITY()
    END
    ELSE
    BEGIN
        UPDATE dbo.DailyWorkEntry
        SET    WorkDate        = @when,
               WorkDone        = @workDone,
               StoryRef        = @story,
               TeamID          = @prmTeamID,
               DailyWorkKindID = @prmDailyWorkKindID,
               MinutesSpent    = @prmMinutesSpent,
               IsFollowUp      = ISNULL(@prmIsFollowUp, 0),
               Notes           = @notes,
               ModifiedBy      = @prmUser,
               ModifiedDate    = GETDATE()
        WHERE  DailyWorkEntryID = @id
    END

    -- Contacts: drop the ones no longer on it, add the new ones, keep the picked order.
    DELETE x FROM dbo.DailyWorkEntryContact x
    WHERE  x.DailyWorkEntryID = @id
      AND  NOT EXISTS (SELECT 1 FROM @contacts c WHERE c.ContactID = x.ContactID)

    INSERT INTO dbo.DailyWorkEntryContact (DailyWorkEntryID, ContactID, SortOrder)
    SELECT @id, c.ContactID, MIN(c.Seq) * 10
    FROM   @contacts c
    WHERE  NOT EXISTS (SELECT 1 FROM dbo.DailyWorkEntryContact x WHERE x.DailyWorkEntryID = @id AND x.ContactID = c.ContactID)
    GROUP BY c.ContactID

    UPDATE x SET SortOrder = o.SortOrder
    FROM   dbo.DailyWorkEntryContact x
    JOIN   (SELECT ContactID, MIN(Seq) * 10 AS SortOrder FROM @contacts GROUP BY ContactID) o ON o.ContactID = x.ContactID
    WHERE  x.DailyWorkEntryID = @id AND x.SortOrder <> o.SortOrder

    -- Links: replaced as a set when they changed. Compared in a binary collation: a URL's path is
    -- case sensitive, so "/Page" -> "/page" is a change (the database collation would call it equal).
    IF EXISTS (SELECT LinkUrl COLLATE Latin1_General_BIN2, SortOrder FROM dbo.DailyWorkEntryLink WHERE DailyWorkEntryID = @id
               EXCEPT SELECT LinkUrl COLLATE Latin1_General_BIN2, Seq * 10 FROM @links)
       OR EXISTS (SELECT LinkUrl COLLATE Latin1_General_BIN2, Seq * 10 FROM @links
                  EXCEPT SELECT LinkUrl COLLATE Latin1_General_BIN2, SortOrder FROM dbo.DailyWorkEntryLink WHERE DailyWorkEntryID = @id)
    BEGIN
        DELETE FROM dbo.DailyWorkEntryLink WHERE DailyWorkEntryID = @id
        INSERT INTO dbo.DailyWorkEntryLink (DailyWorkEntryID, LinkUrl, SortOrder)
        SELECT @id, LinkUrl, Seq * 10 FROM @links ORDER BY Seq
    END

    -- A contact with no team yet joins this entry's team.
    IF @prmTeamID IS NOT NULL
        UPDATE c SET TeamID = @prmTeamID, ModifiedDate = GETDATE()
        FROM   dbo.Contact c
        WHERE  c.TeamID IS NULL
          AND  c.ContactID IN (SELECT ContactID FROM @contacts)

COMMIT

SELECT @id AS DailyWorkEntryID

GO
