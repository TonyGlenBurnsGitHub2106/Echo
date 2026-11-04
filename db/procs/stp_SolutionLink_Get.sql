SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_SolutionLink_Get') IS NOT NULL DROP PROCEDURE dbo.stp_SolutionLink_Get
GO

CREATE PROCEDURE dbo.stp_SolutionLink_Get
    @prmSolutionLinkID INT
AS

/*

TEST
----

    EXEC stp_SolutionLink_Get 1

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------------------------------------
11/02/2026  Felix        Solution links: one link, as stored (the service checks its solution before a delete).

*/

SET NOCOUNT ON

SELECT SolutionLinkID, SolutionID, FromKind, FromID, ToKind, ToID, CreatedByMemberID, CreatedDate
FROM   dbo.SolutionLink
WHERE  SolutionLinkID = @prmSolutionLinkID

GO
