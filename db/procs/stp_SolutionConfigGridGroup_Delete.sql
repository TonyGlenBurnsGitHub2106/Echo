SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_SolutionConfigGridGroup_Delete') IS NOT NULL DROP PROCEDURE dbo.stp_SolutionConfigGridGroup_Delete
GO
CREATE PROCEDURE dbo.stp_SolutionConfigGridGroup_Delete
    @prmSolutionConfigGridGroupID INT
AS

/*

TEST
----

    EXEC stp_SolutionConfigGridGroup_Delete 0

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------------------------------------
10/09/2026  Felix        Story 5611 Solution Configuration II: remove a property group with its properties and every cell of them,
                         in one transaction. Returns RowsAffected.

*/

SET NOCOUNT ON

DECLARE @rows INT = 0
SET XACT_ABORT ON
BEGIN TRY
    BEGIN TRANSACTION
    DELETE a FROM dbo.SolutionConfigGridValueAlsoSet a
    JOIN   dbo.SolutionConfigGridValue v    ON v.SolutionConfigGridValueID    = a.SolutionConfigGridValueID
    JOIN   dbo.SolutionConfigGridProperty p ON p.SolutionConfigGridPropertyID = v.SolutionConfigGridPropertyID
    WHERE  p.SolutionConfigGridGroupID = @prmSolutionConfigGridGroupID
    DELETE v FROM dbo.SolutionConfigGridValue v
    JOIN   dbo.SolutionConfigGridProperty p ON p.SolutionConfigGridPropertyID = v.SolutionConfigGridPropertyID
    WHERE  p.SolutionConfigGridGroupID = @prmSolutionConfigGridGroupID
    DELETE FROM dbo.SolutionConfigGridProperty WHERE SolutionConfigGridGroupID = @prmSolutionConfigGridGroupID
    DELETE FROM dbo.SolutionConfigGridGroup    WHERE SolutionConfigGridGroupID = @prmSolutionConfigGridGroupID
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
