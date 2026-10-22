SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_SolutionTestResult_Set') IS NOT NULL DROP PROCEDURE dbo.stp_SolutionTestResult_Set
GO
CREATE PROCEDURE dbo.stp_SolutionTestResult_Set
    @prmSolutionTestCaseID INT,
    @prmIsFailing          BIT            = NULL,   -- NULL = keep
    @prmResultNote         NVARCHAR(4000) = NULL,   -- NULL = keep, '' = clear
    @prmMemberCode         VARCHAR(20)    = NULL
AS

/*

TEST
----

    EXEC stp_SolutionTestResult_Set 1, 1, N'Save does nothing on the phone', 'tony'

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------------------------------------
09/25/2026  Felix        Solution testing checklists: mark one test case failing or not, and / or set its result note (what
                         is failing). A merge: a NULL parameter keeps what is there. FailingDate is stamped when the case
                         turns failing and cleared when it stops. Upserts under a lock so two quick taps never make two rows.
                         Returns the result row.

*/

SET NOCOUNT ON

IF NOT EXISTS (SELECT 1 FROM dbo.SolutionTestCase WHERE SolutionTestCaseID = @prmSolutionTestCaseID)
BEGIN RAISERROR('That test case no longer exists.', 16, 1) RETURN END

DECLARE @memberId INT = NULL
IF ISNULL(@prmMemberCode, '') <> '' EXEC dbo.stp_SolutionMember_FindOrCreate @prmMemberCode, @memberId OUTPUT

DECLARE @note NVARCHAR(4000) = CASE WHEN @prmResultNote IS NULL THEN NULL ELSE NULLIF(LTRIM(RTRIM(@prmResultNote)), N'') END

BEGIN TRY
    BEGIN TRANSACTION
    UPDATE dbo.SolutionTestResult WITH (UPDLOCK, HOLDLOCK)
    SET    FailingDate = CASE WHEN @prmIsFailing IS NULL THEN FailingDate
                              WHEN @prmIsFailing = 1 THEN CASE WHEN IsFailing = 1 THEN FailingDate ELSE GETDATE() END
                              ELSE NULL END,
           IsFailing   = ISNULL(@prmIsFailing, IsFailing),
           ResultNote  = CASE WHEN @prmResultNote IS NULL THEN ResultNote ELSE @note END,
           ModifiedByMemberID = ISNULL(@memberId, ModifiedByMemberID),
           ModifiedDate = GETDATE()
    WHERE  SolutionTestCaseID = @prmSolutionTestCaseID
    IF @@ROWCOUNT = 0
        INSERT INTO dbo.SolutionTestResult (SolutionTestCaseID, IsFailing, FailingDate, ResultNote, ModifiedByMemberID)
        VALUES (@prmSolutionTestCaseID, ISNULL(@prmIsFailing, 0), CASE WHEN @prmIsFailing = 1 THEN GETDATE() ELSE NULL END,
                @note, @memberId)
    COMMIT TRANSACTION
END TRY
BEGIN CATCH
    IF @@TRANCOUNT > 0 ROLLBACK TRANSACTION
    DECLARE @msg NVARCHAR(2000) = ERROR_MESSAGE()
    RAISERROR(@msg, 16, 1)
    RETURN
END CATCH

SELECT r.SolutionTestResultID, r.SolutionTestCaseID, r.IsFailing, r.FailingDate, r.ResultNote, r.ModifiedDate,
       m.MemberCode AS ResultByCode, m.MemberName AS ResultByName
FROM   dbo.SolutionTestResult r
LEFT JOIN dbo.SolutionMember m ON m.SolutionMemberID = r.ModifiedByMemberID
WHERE  r.SolutionTestCaseID = @prmSolutionTestCaseID


GO
