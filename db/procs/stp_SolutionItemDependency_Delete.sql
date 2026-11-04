SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_SolutionItemDependency_Delete') IS NOT NULL DROP PROCEDURE dbo.stp_SolutionItemDependency_Delete
GO
CREATE PROCEDURE dbo.stp_SolutionItemDependency_Delete
    @prmSolutionItemDependencyID INT
AS

/*

TEST
----

    EXEC stp_SolutionItemDependency_Delete 1

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------------------------------------
11/04/2026  Felix        Story 5695 (Echo): an item no longer waits on another. Both items stay. Returns RowsAffected.

*/

SET NOCOUNT ON
SET XACT_ABORT ON

DELETE FROM dbo.SolutionItemDependency WHERE SolutionItemDependencyID = @prmSolutionItemDependencyID
SELECT @@ROWCOUNT AS RowsAffected

GO
