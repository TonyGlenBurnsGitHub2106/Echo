SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_SolutionMemberVisit_Get') IS NOT NULL DROP PROCEDURE dbo.stp_SolutionMemberVisit_Get
GO

CREATE PROCEDURE dbo.stp_SolutionMemberVisit_Get
    @prmSolutionID INT,
    @prmMemberCode VARCHAR(20)
AS

/*

TEST
----

    EXEC stp_SolutionMemberVisit_Get 6, 'tony'

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------------------------------------
11/02/2026  Felix        Since you were last here: where a member's "since" starts, without moving it. No row
                         (the member never had the solution open) returns nothing.

*/

SET NOCOUNT ON

SELECT v.PreviousSeenDate, v.LastSeenDate
FROM   dbo.SolutionMemberVisit v
JOIN   dbo.SolutionMember m ON m.SolutionMemberID = v.SolutionMemberID
WHERE  v.SolutionID = @prmSolutionID AND m.MemberCode = @prmMemberCode

GO
