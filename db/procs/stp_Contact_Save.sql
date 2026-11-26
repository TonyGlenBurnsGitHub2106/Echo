SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_Contact_Save') IS NOT NULL DROP PROCEDURE dbo.stp_Contact_Save
GO
CREATE PROCEDURE dbo.stp_Contact_Save
    @prmContactName NVARCHAR(150),
    @prmTeamID      INT = NULL
AS

/*

Story 6852 (Daily Work): find or create a contact by name (the "+ Add" row of the Contacts
dropdown). @prmTeamID is the team chosen on the page at that moment, if any: a new contact
joins it, and an existing contact with no team yet is given it. An existing contact's team is
never changed here. Returns ContactID, ContactName, TeamID, TeamName.

TEST
----

    BEGIN TRAN; EXEC dbo.stp_Contact_Save @prmContactName = N'Jill', @prmTeamID = NULL; ROLLBACK

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------
2026-11-25  Felix        New (Story 6852).

*/

SET NOCOUNT ON
SET XACT_ABORT ON

-- A tab is the item separator in vw_DailyWorkEntry.ContactList, so a name never holds one.
DECLARE @name NVARCHAR(150) = LTRIM(RTRIM(REPLACE(ISNULL(@prmContactName, N''), NCHAR(9), N' ')))
IF @name = N''
BEGIN
    RAISERROR('A contact needs a name. Nothing was saved.', 16, 1)
    RETURN
END
IF @prmTeamID IS NOT NULL AND NOT EXISTS (SELECT 1 FROM dbo.Team WHERE TeamID = @prmTeamID)
BEGIN
    RAISERROR('That team is not in Echo. Nothing was saved.', 16, 1)
    RETURN
END

DECLARE @id INT
SELECT @id = ContactID FROM dbo.Contact WHERE ContactName = @name

BEGIN TRAN
    IF @id IS NULL
    BEGIN
        INSERT INTO dbo.Contact (ContactName, TeamID) VALUES (@name, @prmTeamID)
        SET @id = SCOPE_IDENTITY()
    END
    ELSE
        UPDATE dbo.Contact
        SET    IsActive     = 1,
               TeamID       = ISNULL(TeamID, @prmTeamID),
               ModifiedDate = GETDATE()
        WHERE  ContactID = @id
          AND  (IsActive = 0 OR (TeamID IS NULL AND @prmTeamID IS NOT NULL))
COMMIT

SELECT c.ContactID, c.ContactName, c.TeamID, t.TeamName
FROM   dbo.Contact c
LEFT JOIN dbo.Team t ON t.TeamID = c.TeamID
WHERE  c.ContactID = @id

GO
