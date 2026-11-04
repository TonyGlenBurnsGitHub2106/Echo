SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_SolutionMemberVisit_Touch') IS NOT NULL DROP PROCEDURE dbo.stp_SolutionMemberVisit_Touch
GO

CREATE PROCEDURE dbo.stp_SolutionMemberVisit_Touch
    @prmSolutionID  INT,
    @prmMemberCode  VARCHAR(20),
    @prmGapMinutes  INT = 60
AS

/*

TEST
----

    EXEC stp_SolutionMemberVisit_Touch 6, 'tony', 60

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------------------------------------
11/02/2026  Felix        Since you were last here: a member opened a solution. When the last time they had it
                         open was more than @prmGapMinutes ago, that was a different visit, so it becomes the
                         start of "since" (PreviousSeenDate); within the gap it is the same visit and only
                         LastSeenDate moves, so a reload never empties the band. Returns PreviousSeenDate (NULL
                         on a member's first visit) and LastSeenDate.

*/

SET NOCOUNT ON

IF NOT EXISTS (SELECT 1 FROM dbo.Solution WHERE SolutionID = @prmSolutionID)
BEGIN RAISERROR('That solution no longer exists.', 16, 1) RETURN END
IF ISNULL(@prmMemberCode, '') = ''
BEGIN RAISERROR('A visit needs a member.', 16, 1) RETURN END

DECLARE @memberId INT = NULL
EXEC dbo.stp_SolutionMember_FindOrCreate @prmMemberCode, @memberId OUTPUT

DECLARE @now DATETIME = GETDATE()
DECLARE @gap INT = CASE WHEN ISNULL(@prmGapMinutes, 0) > 0 THEN @prmGapMinutes ELSE 60 END

SET XACT_ABORT ON
BEGIN TRY
    BEGIN TRANSACTION
    UPDATE dbo.SolutionMemberVisit WITH (UPDLOCK, HOLDLOCK)
    SET    PreviousSeenDate = CASE WHEN LastSeenDate < DATEADD(MINUTE, -@gap, @now) THEN LastSeenDate ELSE PreviousSeenDate END,
           LastSeenDate     = @now,
           ModifiedDate     = @now
    WHERE  SolutionID = @prmSolutionID AND SolutionMemberID = @memberId
    IF @@ROWCOUNT = 0
        INSERT INTO dbo.SolutionMemberVisit (SolutionID, SolutionMemberID, LastSeenDate, PreviousSeenDate)
        VALUES (@prmSolutionID, @memberId, @now, NULL)
    COMMIT TRANSACTION
END TRY
BEGIN CATCH
    IF @@TRANCOUNT > 0 ROLLBACK TRANSACTION
    DECLARE @msg NVARCHAR(2000) = ERROR_MESSAGE()
    RAISERROR(@msg, 16, 1)
    RETURN
END CATCH

SELECT PreviousSeenDate, LastSeenDate
FROM   dbo.SolutionMemberVisit
WHERE  SolutionID = @prmSolutionID AND SolutionMemberID = @memberId

GO
