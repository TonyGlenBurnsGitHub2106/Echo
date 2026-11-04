SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_SolutionMemberVisit_SaveNote') IS NOT NULL DROP PROCEDURE dbo.stp_SolutionMemberVisit_SaveNote
GO
CREATE PROCEDURE dbo.stp_SolutionMemberVisit_SaveNote
    @prmSolutionID INT,
    @prmMemberCode VARCHAR(20),
    @prmNote       NVARCHAR(4000)   -- '' or NULL = take the note away
AS

/*

TEST
----

    EXEC stp_SolutionMemberVisit_SaveNote 6, 'tony', N'Start with the charset sign-off; Calvin owes the QA dump.'

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------------------------------------
11/04/2026  Felix        Story 5695 (Echo): where I left off -- a member's note to themself for next time, one per
                         member per solution, kept on the visit row (made if the member never had a visit). A new note
                         replaces the old; blank takes it away (and writes nothing for a member with no visit, who
                         had no note to take). At most 1,000 characters. Returns RowsAffected.

*/

SET NOCOUNT ON

IF NOT EXISTS (SELECT 1 FROM dbo.Solution WHERE SolutionID = @prmSolutionID)
BEGIN RAISERROR('That solution no longer exists. Nothing was saved.', 16, 1) RETURN END
IF ISNULL(@prmMemberCode, '') = '' BEGIN RAISERROR('Whose note it is was not said. Nothing was saved.', 16, 1) RETURN END
DECLARE @note NVARCHAR(4000) = NULLIF(LTRIM(RTRIM(ISNULL(@prmNote, N''))), N'')
IF LEN(ISNULL(@note, N'')) > 1000 BEGIN RAISERROR('A note for next time can be at most 1,000 characters. Nothing was saved.', 16, 1) RETURN END

IF @note IS NULL AND NOT EXISTS (SELECT 1 FROM dbo.SolutionMemberVisit v JOIN dbo.SolutionMember m ON m.SolutionMemberID = v.SolutionMemberID
                                 WHERE v.SolutionID = @prmSolutionID AND m.MemberCode = LOWER(LTRIM(RTRIM(@prmMemberCode))))
BEGIN SELECT 0 AS RowsAffected RETURN END

DECLARE @memberId INT = NULL
EXEC dbo.stp_SolutionMember_FindOrCreate @prmMemberCode, @memberId OUTPUT

SET XACT_ABORT ON
BEGIN TRY
    BEGIN TRANSACTION
    IF EXISTS (SELECT 1 FROM dbo.SolutionMemberVisit WITH (UPDLOCK, HOLDLOCK) WHERE SolutionID = @prmSolutionID AND SolutionMemberID = @memberId)
        UPDATE dbo.SolutionMemberVisit
        SET    PartingNote = @note, PartingNoteDate = CASE WHEN @note IS NULL THEN NULL ELSE GETDATE() END, ModifiedDate = GETDATE()
        WHERE  SolutionID = @prmSolutionID AND SolutionMemberID = @memberId
    ELSE
        INSERT INTO dbo.SolutionMemberVisit (SolutionID, SolutionMemberID, LastSeenDate, PreviousSeenDate, PartingNote, PartingNoteDate)
        VALUES (@prmSolutionID, @memberId, GETDATE(), NULL, @note, CASE WHEN @note IS NULL THEN NULL ELSE GETDATE() END)
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
