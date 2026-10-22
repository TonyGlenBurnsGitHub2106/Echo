SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_SolutionLog_Save') IS NOT NULL DROP PROCEDURE dbo.stp_SolutionLog_Save
GO
CREATE PROCEDURE dbo.stp_SolutionLog_Save
    @prmSolutionLogID         INT,
    @prmSolutionID            INT,
    @prmSolutionProjectID     INT,
    @prmSolutionProjectItemID INT,
    @prmLogTypeCode           VARCHAR(20),
    @prmAuthorCode            VARCHAR(20),
    @prmLogText               NVARCHAR(MAX),
    @prmLogDate               DATETIME
AS

/*

TEST
----

    EXEC stp_SolutionLog_Save 0, 1, NULL, NULL, 'note', 'felix', 'What was done.', NULL

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------------------------------------
09/17/2026  Felix        Story 3493: append (id = 0) or correct one journal entry. A new entry takes the next
                         SequenceNumber of its solution under a lock, so two writers can never share a number
                         and "what was done in what order" stays a single ordered read. An entry that names an
                         item inherits that item's project. An edit never changes the sequence, the solution
                         or the author. Returns SolutionLogID and SequenceNumber.

*/

SET NOCOUNT ON

SET @prmLogText = LTRIM(RTRIM(ISNULL(@prmLogText, '')))
IF @prmLogText = '' BEGIN RAISERROR('A log entry needs some text.', 16, 1) RETURN END
IF ISNULL(@prmSolutionProjectID, 0)     <= 0 SET @prmSolutionProjectID = NULL
IF ISNULL(@prmSolutionProjectItemID, 0) <= 0 SET @prmSolutionProjectItemID = NULL
IF @prmLogDate IS NULL SET @prmLogDate = GETDATE()

IF @prmSolutionLogID > 0 SET @prmSolutionID = (SELECT SolutionID FROM dbo.SolutionLog WHERE SolutionLogID = @prmSolutionLogID)
IF NOT EXISTS (SELECT 1 FROM dbo.Solution WHERE SolutionID = @prmSolutionID) BEGIN
    RAISERROR('That solution no longer exists.', 16, 1) RETURN
END

IF @prmSolutionProjectItemID IS NOT NULL BEGIN
    DECLARE @itemProjectId INT, @itemSolutionId INT
    SELECT @itemProjectId = i.SolutionProjectID, @itemSolutionId = p.SolutionID
    FROM   dbo.SolutionProjectItem i JOIN dbo.SolutionProject p ON p.SolutionProjectID = i.SolutionProjectID
    WHERE  i.SolutionProjectItemID = @prmSolutionProjectItemID
    IF ISNULL(@itemSolutionId, 0) <> @prmSolutionID BEGIN
        RAISERROR('That item does not belong to this solution.', 16, 1) RETURN
    END
    SET @prmSolutionProjectID = @itemProjectId
END
IF @prmSolutionProjectID IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM dbo.SolutionProject WHERE SolutionProjectID = @prmSolutionProjectID AND SolutionID = @prmSolutionID) BEGIN
    RAISERROR('That project does not belong to this solution.', 16, 1) RETURN
END

DECLARE @typeId INT = (SELECT SolutionLogTypeID FROM dbo.SolutionLogType WHERE LogTypeCode = LOWER(LTRIM(RTRIM(ISNULL(@prmLogTypeCode, '')))))
IF @typeId IS NULL SET @typeId = (SELECT TOP 1 SolutionLogTypeID FROM dbo.SolutionLogType ORDER BY SortOrder, SolutionLogTypeID)

DECLARE @seq INT
IF @prmSolutionLogID > 0 BEGIN
    UPDATE dbo.SolutionLog
    SET    SolutionProjectID = @prmSolutionProjectID, SolutionProjectItemID = @prmSolutionProjectItemID,
           SolutionLogTypeID = @typeId, LogText = @prmLogText, ModifiedDate = GETDATE()
    WHERE  SolutionLogID = @prmSolutionLogID
    SET @seq = (SELECT SequenceNumber FROM dbo.SolutionLog WHERE SolutionLogID = @prmSolutionLogID)
END ELSE BEGIN
    DECLARE @authorId INT
    EXEC dbo.stp_SolutionMember_FindOrCreate @prmAuthorCode, @authorId OUTPUT

    BEGIN TRY
        BEGIN TRANSACTION
        SET @seq = ISNULL((SELECT MAX(SequenceNumber) FROM dbo.SolutionLog WITH (UPDLOCK, HOLDLOCK) WHERE SolutionID = @prmSolutionID), 0) + 1
        INSERT INTO dbo.SolutionLog (SolutionID, SolutionProjectID, SolutionProjectItemID, SequenceNumber, SolutionLogTypeID,
                                     AuthorMemberID, LogText, LogDate)
        VALUES (@prmSolutionID, @prmSolutionProjectID, @prmSolutionProjectItemID, @seq, @typeId, @authorId, @prmLogText, @prmLogDate)
        SET @prmSolutionLogID = SCOPE_IDENTITY()
        COMMIT TRANSACTION
    END TRY
    BEGIN CATCH
        IF @@TRANCOUNT > 0 ROLLBACK TRANSACTION
        DECLARE @msg NVARCHAR(2000) = ERROR_MESSAGE()
        RAISERROR(@msg, 16, 1)
        RETURN
    END CATCH
END

SELECT @prmSolutionLogID AS SolutionLogID, @seq AS SequenceNumber

GO
