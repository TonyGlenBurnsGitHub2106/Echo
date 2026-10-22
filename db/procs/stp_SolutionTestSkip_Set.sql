SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_SolutionTestSkip_Set') IS NOT NULL DROP PROCEDURE dbo.stp_SolutionTestSkip_Set
GO
CREATE PROCEDURE dbo.stp_SolutionTestSkip_Set
    @prmSolutionTestCaseID      INT,
    @prmSolutionTestPlanLevelID INT,
    @prmIsSkipped               BIT,
    @prmMemberCode              VARCHAR(20)
AS

/*

TEST
----

    EXEC stp_SolutionTestSkip_Set 1, 1, 1, 'tony'

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------------------------------------
09/25/2026  Felix        Story 3534: skip or unskip one cell (a case in a column) of a testing checklist. A skipped cell is
                         neither a pass nor a fail. Skipping also UNTICKS the cell -- a cell is never both. The case and the
                         column must belong to the same checklist. A skip records who and when; an unskip clears both (the
                         _LOG twin keeps the history). Upserts under a lock so two quick taps never make two rows. Returns
                         the cell.

*/

SET NOCOUNT ON

DECLARE @casePlan INT, @levelPlan INT
SELECT @casePlan = g.SolutionTestPlanID
FROM   dbo.SolutionTestCase c JOIN dbo.SolutionTestGroup g ON g.SolutionTestGroupID = c.SolutionTestGroupID
WHERE  c.SolutionTestCaseID = @prmSolutionTestCaseID
SELECT @levelPlan = SolutionTestPlanID FROM dbo.SolutionTestPlanLevel WHERE SolutionTestPlanLevelID = @prmSolutionTestPlanLevelID
IF @casePlan IS NULL  BEGIN RAISERROR('That test case no longer exists.', 16, 1) RETURN END
IF @levelPlan IS NULL BEGIN RAISERROR('That column no longer exists.', 16, 1) RETURN END
IF @casePlan <> @levelPlan BEGIN RAISERROR('That column belongs to a different checklist.', 16, 1) RETURN END

DECLARE @skip BIT = ISNULL(@prmIsSkipped, 0)
DECLARE @memberId INT = NULL
IF @skip = 1 EXEC dbo.stp_SolutionMember_FindOrCreate @prmMemberCode, @memberId OUTPUT

BEGIN TRY
    BEGIN TRANSACTION
    UPDATE dbo.SolutionTestSkip WITH (UPDLOCK, HOLDLOCK)
    SET    IsSkipped = @skip,
           SkippedDate = CASE WHEN @skip = 1 THEN GETDATE() ELSE NULL END,
           SkippedByMemberID = @memberId, ModifiedDate = GETDATE()
    WHERE  SolutionTestCaseID = @prmSolutionTestCaseID AND SolutionTestPlanLevelID = @prmSolutionTestPlanLevelID
    IF @@ROWCOUNT = 0 AND @skip = 1
        INSERT INTO dbo.SolutionTestSkip (SolutionTestCaseID, SolutionTestPlanLevelID, IsSkipped, SkippedDate, SkippedByMemberID)
        VALUES (@prmSolutionTestCaseID, @prmSolutionTestPlanLevelID, 1, GETDATE(), @memberId)

    -- A skipped cell is not ticked.
    IF @skip = 1
        UPDATE dbo.SolutionTestCheck
        SET    IsChecked = 0, CheckedDate = NULL, CheckedByMemberID = NULL, ModifiedDate = GETDATE()
        WHERE  SolutionTestCaseID = @prmSolutionTestCaseID AND SolutionTestPlanLevelID = @prmSolutionTestPlanLevelID
        AND    IsChecked = 1
    COMMIT TRANSACTION
END TRY
BEGIN CATCH
    IF @@TRANCOUNT > 0 ROLLBACK TRANSACTION
    DECLARE @msg NVARCHAR(2000) = ERROR_MESSAGE()
    RAISERROR(@msg, 16, 1)
    RETURN
END CATCH

SELECT @prmSolutionTestCaseID AS SolutionTestCaseID, @prmSolutionTestPlanLevelID AS SolutionTestPlanLevelID,
       CAST(ISNULL(s.IsSkipped, 0) AS BIT) AS IsSkipped, s.SkippedDate,
       m.MemberCode AS SkippedByCode, m.MemberName AS SkippedByName
FROM   (SELECT 1 AS one) x
LEFT JOIN dbo.SolutionTestSkip s ON s.SolutionTestCaseID = @prmSolutionTestCaseID AND s.SolutionTestPlanLevelID = @prmSolutionTestPlanLevelID
LEFT JOIN dbo.SolutionMember m   ON m.SolutionMemberID = s.SkippedByMemberID


GO
