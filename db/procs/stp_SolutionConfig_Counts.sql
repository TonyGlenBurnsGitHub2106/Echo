SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_SolutionConfig_Counts') IS NOT NULL DROP PROCEDURE dbo.stp_SolutionConfig_Counts
GO
CREATE PROCEDURE dbo.stp_SolutionConfig_Counts
    @prmSolutionID INT
AS

/*

TEST
----

    EXEC stp_SolutionConfig_Counts 6

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------------------------------------
09/21/2026  Felix        Story 3516 Solution configuration: the counters of one solution's Configuration tab as Bucket /
                         BucketKey / Cnt rows -- properties (all), state:<match state>, group:<group> properties in the group,
                         groupstate:<group>.<match state>.

*/

SET NOCOUNT ON

SELECT 'properties' AS Bucket, CAST('' AS VARCHAR(40)) AS BucketKey, COUNT(*) AS Cnt
FROM   dbo.vw_SolutionConfigPropertyState WHERE SolutionID = @prmSolutionID
UNION ALL
SELECT 'state', CAST(MatchState AS VARCHAR(40)), COUNT(*)
FROM   dbo.vw_SolutionConfigPropertyState WHERE SolutionID = @prmSolutionID GROUP BY MatchState
UNION ALL
SELECT 'group', CAST(SolutionConfigGroupID AS VARCHAR(40)), COUNT(*)
FROM   dbo.vw_SolutionConfigPropertyState WHERE SolutionID = @prmSolutionID GROUP BY SolutionConfigGroupID
UNION ALL
SELECT 'groupstate', CAST(SolutionConfigGroupID AS VARCHAR(20)) + '.' + MatchState, COUNT(*)
FROM   dbo.vw_SolutionConfigPropertyState WHERE SolutionID = @prmSolutionID GROUP BY SolutionConfigGroupID, MatchState

GO
