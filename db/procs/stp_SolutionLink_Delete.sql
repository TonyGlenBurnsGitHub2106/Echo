SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_SolutionLink_Delete') IS NOT NULL DROP PROCEDURE dbo.stp_SolutionLink_Delete
GO

CREATE PROCEDURE dbo.stp_SolutionLink_Delete
    @prmSolutionLinkID INT
AS

/*

TEST
----

    EXEC stp_SolutionLink_Delete 0

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------------------------------------
11/02/2026  Felix        Solution links: remove one link. Neither record is touched. Returns RowsAffected.

*/

SET NOCOUNT ON

DELETE FROM dbo.SolutionLink WHERE SolutionLinkID = @prmSolutionLinkID
SELECT @@ROWCOUNT AS RowsAffected

GO
