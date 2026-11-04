SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_SolutionDecision_Delete') IS NOT NULL DROP PROCEDURE dbo.stp_SolutionDecision_Delete
GO
CREATE PROCEDURE dbo.stp_SolutionDecision_Delete
    @prmSolutionDecisionID INT
AS

/*

TEST
----

    EXEC stp_SolutionDecision_Delete 1

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------------------------------------
11/04/2026  Felix        Story 5695 (Echo): remove one decision, in one transaction. What rested on it is put back:
                         the questions it answered are open again, and the decision it had replaced is accepted
                         again. Its links go; its journal entry stays (the journal is history). Returns RowsAffected.

*/

SET NOCOUNT ON
SET XACT_ABORT ON

DECLARE @rows INT = 0
BEGIN TRY
    BEGIN TRANSACTION
    UPDATE dbo.SolutionQuestion
    SET    StatusCode = 'open', AnsweredBySolutionDecisionID = NULL, AnsweredDate = NULL, ModifiedDate = GETDATE()
    WHERE  AnsweredBySolutionDecisionID = @prmSolutionDecisionID
    UPDATE dbo.SolutionDecision
    SET    StatusCode = 'accepted', SupersededBySolutionDecisionID = NULL, ModifiedDate = GETDATE()
    WHERE  SupersededBySolutionDecisionID = @prmSolutionDecisionID
    DELETE FROM dbo.SolutionLink
    WHERE  (FromKind = 'decision' AND FromID = @prmSolutionDecisionID) OR (ToKind = 'decision' AND ToID = @prmSolutionDecisionID)
    DELETE FROM dbo.SolutionDecision WHERE SolutionDecisionID = @prmSolutionDecisionID
    SET @rows = @@ROWCOUNT
    COMMIT TRANSACTION
END TRY
BEGIN CATCH
    IF @@TRANCOUNT > 0 ROLLBACK TRANSACTION
    DECLARE @msg NVARCHAR(2000) = ERROR_MESSAGE()
    RAISERROR(@msg, 16, 1)
    RETURN
END CATCH

SELECT @rows AS RowsAffected

GO
