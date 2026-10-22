SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_SolutionTestCheck_Set') IS NOT NULL DROP PROCEDURE dbo.stp_SolutionTestCheck_Set
GO
CREATE PROCEDURE dbo.stp_SolutionTestCheck_Set
    @prmSolutionTestCaseID      INT,
    @prmSolutionTestPlanLevelID INT,
    @prmIsChecked               BIT,
    @prmMemberCode              VARCHAR(20)
AS

/*

TEST
----

    EXEC stp_SolutionTestCheck_Set 1, 1, 1, 'tony'

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------------------------------------
09/21/2026  Felix        Solution testing checklists: tick or untick one cell (a case in a column). The case and the column must
                         belong to the same checklist. A tick records who and when; an untick clears both (the _LOG twin keeps
                         the history). Upserts under a lock so two quick taps never make two rows. Returns the cell.
09/25/2026  Felix        Story 3534: ticking a cell also UNSKIPS it -- a cell is never both ticked and skipped.

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

DECLARE @memberId INT = NULL
IF ISNULL(@prmIsChecked, 0) = 1 EXEC dbo.stp_SolutionMember_FindOrCreate @prmMemberCode, @memberId OUTPUT

BEGIN TRY
    BEGIN TRANSACTION
    UPDATE dbo.SolutionTestCheck WITH (UPDLOCK, HOLDLOCK)
    SET    IsChecked = ISNULL(@prmIsChecked, 0),
           CheckedDate = CASE WHEN ISNULL(@prmIsChecked, 0) = 1 THEN GETDATE() ELSE NULL END,
           CheckedByMemberID = @memberId, ModifiedDate = GETDATE()
    WHERE  SolutionTestCaseID = @prmSolutionTestCaseID AND SolutionTestPlanLevelID = @prmSolutionTestPlanLevelID
    IF @@ROWCOUNT = 0
        INSERT INTO dbo.SolutionTestCheck (SolutionTestCaseID, SolutionTestPlanLevelID, IsChecked, CheckedDate, CheckedByMemberID)
        VALUES (@prmSolutionTestCaseID, @prmSolutionTestPlanLevelID, ISNULL(@prmIsChecked, 0),
                CASE WHEN ISNULL(@prmIsChecked, 0) = 1 THEN GETDATE() ELSE NULL END, @memberId)
    -- A ticked cell is not skipped (Story 3534).
    IF ISNULL(@prmIsChecked, 0) = 1
        UPDATE dbo.SolutionTestSkip
        SET    IsSkipped = 0, SkippedDate = NULL, SkippedByMemberID = NULL, ModifiedDate = GETDATE()
        WHERE  SolutionTestCaseID = @prmSolutionTestCaseID AND SolutionTestPlanLevelID = @prmSolutionTestPlanLevelID
        AND    IsSkipped = 1
    COMMIT TRANSACTION
END TRY
BEGIN CATCH
    IF @@TRANCOUNT > 0 ROLLBACK TRANSACTION
    DECLARE @msg NVARCHAR(2000) = ERROR_MESSAGE()
    RAISERROR(@msg, 16, 1)
    RETURN
END CATCH

SELECT k.SolutionTestCheckID, k.SolutionTestCaseID, k.SolutionTestPlanLevelID, k.IsChecked, k.CheckedDate,
       m.MemberCode AS CheckedByCode, m.MemberName AS CheckedByName
FROM   dbo.SolutionTestCheck k
LEFT JOIN dbo.SolutionMember m ON m.SolutionMemberID = k.CheckedByMemberID
WHERE  k.SolutionTestCaseID = @prmSolutionTestCaseID AND k.SolutionTestPlanLevelID = @prmSolutionTestPlanLevelID


GO
