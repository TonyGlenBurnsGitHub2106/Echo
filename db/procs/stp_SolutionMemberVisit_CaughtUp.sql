SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_SolutionMemberVisit_CaughtUp') IS NOT NULL DROP PROCEDURE dbo.stp_SolutionMemberVisit_CaughtUp
GO

CREATE PROCEDURE dbo.stp_SolutionMemberVisit_CaughtUp
    @prmSolutionID INT,
    @prmMemberCode VARCHAR(20)
AS

/*

TEST
----

    EXEC stp_SolutionMemberVisit_CaughtUp 6, 'tony'

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------------------------------------
11/02/2026  Felix        Since you were last here: the member has read it all, so "since" starts now. Makes the
                         visit row when there is none. Returns RowsAffected.

*/

SET NOCOUNT ON

IF NOT EXISTS (SELECT 1 FROM dbo.Solution WHERE SolutionID = @prmSolutionID)
BEGIN RAISERROR('That solution no longer exists.', 16, 1) RETURN END
IF ISNULL(@prmMemberCode, '') = ''
BEGIN RAISERROR('A visit needs a member.', 16, 1) RETURN END

DECLARE @memberId INT = NULL
EXEC dbo.stp_SolutionMember_FindOrCreate @prmMemberCode, @memberId OUTPUT
DECLARE @now DATETIME = GETDATE()

SET XACT_ABORT ON
BEGIN TRY
    BEGIN TRANSACTION
    UPDATE dbo.SolutionMemberVisit WITH (UPDLOCK, HOLDLOCK)
    SET    PreviousSeenDate = @now, LastSeenDate = @now, ModifiedDate = @now
    WHERE  SolutionID = @prmSolutionID AND SolutionMemberID = @memberId
    IF @@ROWCOUNT = 0
        INSERT INTO dbo.SolutionMemberVisit (SolutionID, SolutionMemberID, LastSeenDate, PreviousSeenDate)
        VALUES (@prmSolutionID, @memberId, @now, @now)
    COMMIT TRANSACTION
END TRY
BEGIN CATCH
    IF @@TRANCOUNT > 0 ROLLBACK TRANSACTION
    DECLARE @msg NVARCHAR(2000) = ERROR_MESSAGE()
    RAISERROR(@msg, 16, 1)
    RETURN
END CATCH

SELECT 1 AS RowsAffected

GO
