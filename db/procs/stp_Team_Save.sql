SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_Team_Save') IS NOT NULL DROP PROCEDURE dbo.stp_Team_Save
GO
CREATE PROCEDURE dbo.stp_Team_Save
    @prmTeamName NVARCHAR(100)
AS

/*

Story 6852 (Daily Work): find or create a team by name (the "+ Add" row of the Team dropdown).
A name that is already there (any case) returns the existing team and wakes it if it was
switched off; nothing is added twice. Returns TeamID, TeamName.

TEST
----

    BEGIN TRAN; EXEC dbo.stp_Team_Save @prmTeamName = N'Claims'; ROLLBACK

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------
2026-11-25  Felix        New (Story 6852).

*/

SET NOCOUNT ON
SET XACT_ABORT ON

DECLARE @name NVARCHAR(100) = LTRIM(RTRIM(ISNULL(@prmTeamName, N'')))
IF @name = N''
BEGIN
    RAISERROR('A team needs a name. Nothing was saved.', 16, 1)
    RETURN
END

DECLARE @id INT
SELECT @id = TeamID FROM dbo.Team WHERE TeamName = @name

BEGIN TRAN
    IF @id IS NULL
    BEGIN
        INSERT INTO dbo.Team (TeamName) VALUES (@name)
        SET @id = SCOPE_IDENTITY()
    END
    ELSE
        UPDATE dbo.Team SET IsActive = 1, ModifiedDate = GETDATE() WHERE TeamID = @id AND IsActive = 0
COMMIT

SELECT TeamID, TeamName FROM dbo.Team WHERE TeamID = @id

GO
