SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_SolutionProjectItem_Delete') IS NOT NULL DROP PROCEDURE dbo.stp_SolutionProjectItem_Delete
GO
CREATE PROCEDURE dbo.stp_SolutionProjectItem_Delete
    @prmSolutionProjectItemID INT
AS

/*

TEST
----

    EXEC stp_SolutionProjectItem_Delete 0

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------------------------------------
09/17/2026  Felix        Story 3493: remove one item and every sub-item beneath it. Journal entries that pointed at any
                         of them are HISTORY and are kept, with the pointer cleared. One transaction. Returns
                         RowsAffected -- how many items went, the item itself included.

*/

SET NOCOUNT ON

DECLARE @subtree TABLE (ItemID INT PRIMARY KEY)
;WITH Tree AS (
    SELECT SolutionProjectItemID FROM dbo.SolutionProjectItem WHERE SolutionProjectItemID = @prmSolutionProjectItemID
    UNION ALL
    SELECT c.SolutionProjectItemID FROM dbo.SolutionProjectItem c JOIN Tree t ON c.ParentItemID = t.SolutionProjectItemID)
INSERT INTO @subtree (ItemID) SELECT SolutionProjectItemID FROM Tree

DECLARE @rows INT = 0
BEGIN TRY
    BEGIN TRANSACTION
    UPDATE dbo.SolutionLog  SET SolutionProjectItemID = NULL WHERE SolutionProjectItemID IN (SELECT ItemID FROM @subtree)
    UPDATE dbo.SolutionFile SET SolutionProjectItemID = NULL WHERE SolutionProjectItemID IN (SELECT ItemID FROM @subtree)
    DELETE FROM dbo.SolutionProjectItem WHERE SolutionProjectItemID IN (SELECT ItemID FROM @subtree)
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
