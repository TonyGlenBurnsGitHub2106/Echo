SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_SolutionTopic_Delete') IS NOT NULL DROP PROCEDURE dbo.stp_SolutionTopic_Delete
GO

CREATE PROCEDURE dbo.stp_SolutionTopic_Delete
    @prmSolutionTopicID INT
AS

/*

TEST
----

    EXEC stp_SolutionTopic_Delete 0

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------------------------------------
11/02/2026  Felix        Solution topics: remove one topic and its links, in one transaction. The records that
                         were linked to it stay. Returns RowsAffected.

*/

SET NOCOUNT ON

SET XACT_ABORT ON

DECLARE @rows INT = 0

BEGIN TRY
    BEGIN TRANSACTION
    DELETE FROM dbo.SolutionLink
    WHERE  (FromKind = 'topic' AND FromID = @prmSolutionTopicID) OR (ToKind = 'topic' AND ToID = @prmSolutionTopicID)
    DELETE FROM dbo.SolutionTopic WHERE SolutionTopicID = @prmSolutionTopicID
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
