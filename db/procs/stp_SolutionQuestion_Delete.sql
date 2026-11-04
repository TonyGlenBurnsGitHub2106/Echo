SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_SolutionQuestion_Delete') IS NOT NULL DROP PROCEDURE dbo.stp_SolutionQuestion_Delete
GO
CREATE PROCEDURE dbo.stp_SolutionQuestion_Delete
    @prmSolutionQuestionID INT
AS

/*

TEST
----

    EXEC stp_SolutionQuestion_Delete 1

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------------------------------------
11/04/2026  Felix        Story 5695 (Echo): remove one question and its links, in one transaction. A decision
                         that answered it and the journal entry it came from stay. Returns RowsAffected.

*/

SET NOCOUNT ON
SET XACT_ABORT ON

DECLARE @rows INT = 0
BEGIN TRY
    BEGIN TRANSACTION
    DELETE FROM dbo.SolutionLink
    WHERE  (FromKind = 'question' AND FromID = @prmSolutionQuestionID) OR (ToKind = 'question' AND ToID = @prmSolutionQuestionID)
    DELETE FROM dbo.SolutionQuestion WHERE SolutionQuestionID = @prmSolutionQuestionID
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
