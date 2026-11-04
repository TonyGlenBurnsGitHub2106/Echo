SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_SolutionTopicType_Delete') IS NOT NULL DROP PROCEDURE dbo.stp_SolutionTopicType_Delete
GO
CREATE PROCEDURE dbo.stp_SolutionTopicType_Delete
    @prmSolutionTopicTypeID INT
AS

/*

TEST
----

    EXEC stp_SolutionTopicType_Delete 3

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------------------------------------
11/04/2026  Felix        Story 5695 (Echo): remove one kind of part. Its parts stay, with no kind, in one
                         transaction. Returns RowsAffected.

*/

SET NOCOUNT ON
SET XACT_ABORT ON

DECLARE @rows INT = 0
BEGIN TRY
    BEGIN TRANSACTION
    UPDATE dbo.SolutionTopic SET SolutionTopicTypeID = NULL, ModifiedDate = GETDATE() WHERE SolutionTopicTypeID = @prmSolutionTopicTypeID
    DELETE FROM dbo.SolutionTopicType WHERE SolutionTopicTypeID = @prmSolutionTopicTypeID
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
