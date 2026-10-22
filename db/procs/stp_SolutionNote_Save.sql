SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_SolutionNote_Save') IS NOT NULL DROP PROCEDURE dbo.stp_SolutionNote_Save
GO
CREATE PROCEDURE dbo.stp_SolutionNote_Save
    @prmSolutionNoteID INT,
    @prmSolutionID         INT,
    @prmSolutionProjectID  INT,
    @prmNoteTypeCode        VARCHAR(20),
    @prmNoteTitle      NVARCHAR(300),
    @prmNoteBody       NVARCHAR(MAX),
    @prmChangeNote         NVARCHAR(500),
    @prmAuthorCode         VARCHAR(20),
    @prmSortOrder          INT
AS

/*

TEST
----

    EXEC stp_SolutionNote_Save 0, 1, NULL, 'runbook', 'Sample runbook', 'How to do it.', 'First draft', 'felix', 0

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------------------------------------
09/17/2026  Felix        Story 3493: insert (id = 0) or update one note. The header is updated in place; the BODY is
                         never overwritten -- whenever it differs from the current version a new
                         SolutionNoteVersion row is written and CurrentVersionNumber moves to it, so every
                         earlier wording can still be read. Saving an unchanged body writes no version. One
                         transaction. Returns SolutionNoteID and CurrentVersionNumber.

09/22/2026  Felix        Solutions documents are now NOTES (SolutionDocument* -> SolutionNote*, Tony 09/22/2026).

*/

SET NOCOUNT ON

SET @prmNoteTitle = LTRIM(RTRIM(ISNULL(@prmNoteTitle, '')))
IF @prmNoteTitle = '' BEGIN RAISERROR('A note needs a title.', 16, 1) RETURN END
IF ISNULL(@prmSolutionProjectID, 0) <= 0 SET @prmSolutionProjectID = NULL
IF @prmSolutionNoteID > 0 SET @prmSolutionID = (SELECT SolutionID FROM dbo.SolutionNote WHERE SolutionNoteID = @prmSolutionNoteID)
IF NOT EXISTS (SELECT 1 FROM dbo.Solution WHERE SolutionID = @prmSolutionID) BEGIN
    RAISERROR('That solution no longer exists.', 16, 1) RETURN
END
IF @prmSolutionProjectID IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM dbo.SolutionProject WHERE SolutionProjectID = @prmSolutionProjectID AND SolutionID = @prmSolutionID) BEGIN
    RAISERROR('That project does not belong to this solution.', 16, 1) RETURN
END

DECLARE @typeId INT = (SELECT SolutionNoteTypeID FROM dbo.SolutionNoteType WHERE NoteTypeCode = LOWER(LTRIM(RTRIM(ISNULL(@prmNoteTypeCode, '')))))
IF @typeId IS NULL SET @typeId = (SELECT TOP 1 SolutionNoteTypeID FROM dbo.SolutionNoteType ORDER BY SortOrder, SolutionNoteTypeID)
DECLARE @authorId INT
EXEC dbo.stp_SolutionMember_FindOrCreate @prmAuthorCode, @authorId OUTPUT
DECLARE @version INT

BEGIN TRY
    BEGIN TRANSACTION

    IF @prmSolutionNoteID > 0 BEGIN
        UPDATE dbo.SolutionNote
        SET    SolutionProjectID = @prmSolutionProjectID, SolutionNoteTypeID = @typeId, NoteTitle = @prmNoteTitle,
               SortOrder = CASE WHEN ISNULL(@prmSortOrder, 0) > 0 THEN @prmSortOrder ELSE SortOrder END,
               ModifiedDate = GETDATE(), ModifiedBy = LOWER(@prmAuthorCode)
        WHERE  SolutionNoteID = @prmSolutionNoteID
    END ELSE BEGIN
        INSERT INTO dbo.SolutionNote (SolutionID, SolutionProjectID, SolutionNoteTypeID, NoteTitle, CurrentVersionNumber, SortOrder, ModifiedBy)
        VALUES (@prmSolutionID, @prmSolutionProjectID, @typeId, @prmNoteTitle, 0,
                CASE WHEN ISNULL(@prmSortOrder, 0) > 0 THEN @prmSortOrder
                     ELSE ISNULL((SELECT MAX(SortOrder) FROM dbo.SolutionNote WHERE SolutionID = @prmSolutionID), 0) + 1 END,
                LOWER(@prmAuthorCode))
        SET @prmSolutionNoteID = SCOPE_IDENTITY()
    END

    SET @version = (SELECT CurrentVersionNumber FROM dbo.SolutionNote WHERE SolutionNoteID = @prmSolutionNoteID)
    DECLARE @currentBody NVARCHAR(MAX) = (SELECT NoteBody FROM dbo.SolutionNoteVersion
                                          WHERE SolutionNoteID = @prmSolutionNoteID AND VersionNumber = @version)
    IF @version = 0 OR ISNULL(@currentBody, '') <> ISNULL(@prmNoteBody, '') BEGIN
        SET @version = @version + 1
        INSERT INTO dbo.SolutionNoteVersion (SolutionNoteID, VersionNumber, NoteBody, ChangeNote, AuthorMemberID)
        VALUES (@prmSolutionNoteID, @version, @prmNoteBody, NULLIF(LTRIM(RTRIM(ISNULL(@prmChangeNote, ''))), ''), @authorId)
        UPDATE dbo.SolutionNote SET CurrentVersionNumber = @version WHERE SolutionNoteID = @prmSolutionNoteID
    END

    COMMIT TRANSACTION
END TRY
BEGIN CATCH
    IF @@TRANCOUNT > 0 ROLLBACK TRANSACTION
    DECLARE @msg NVARCHAR(2000) = ERROR_MESSAGE()
    RAISERROR(@msg, 16, 1)
    RETURN
END CATCH

SELECT @prmSolutionNoteID AS SolutionNoteID, @version AS CurrentVersionNumber


GO
