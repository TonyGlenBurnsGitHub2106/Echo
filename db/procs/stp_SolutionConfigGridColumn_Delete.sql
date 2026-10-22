SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_SolutionConfigGridColumn_Delete') IS NOT NULL DROP PROCEDURE dbo.stp_SolutionConfigGridColumn_Delete
GO
CREATE PROCEDURE dbo.stp_SolutionConfigGridColumn_Delete
    @prmSolutionConfigGridColumnID INT
AS

/*

TEST
----

    EXEC stp_SolutionConfigGridColumn_Delete 0

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------------------------------------
10/09/2026  Felix        Story 5611 Solution Configuration II: remove a column with its cells (and their other places) and its
                         axis tags, in one transaction. The properties stay. Returns RowsAffected.

*/

SET NOCOUNT ON

DECLARE @rows INT = 0
SET XACT_ABORT ON
BEGIN TRY
    BEGIN TRANSACTION
    DELETE a FROM dbo.SolutionConfigGridValueAlsoSet a
    JOIN   dbo.SolutionConfigGridValue v ON v.SolutionConfigGridValueID = a.SolutionConfigGridValueID
    WHERE  v.SolutionConfigGridColumnID = @prmSolutionConfigGridColumnID
    DELETE FROM dbo.SolutionConfigGridValue     WHERE SolutionConfigGridColumnID = @prmSolutionConfigGridColumnID
    DELETE FROM dbo.SolutionConfigGridColumnTag WHERE SolutionConfigGridColumnID = @prmSolutionConfigGridColumnID
    DELETE FROM dbo.SolutionConfigGridColumn    WHERE SolutionConfigGridColumnID = @prmSolutionConfigGridColumnID
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
