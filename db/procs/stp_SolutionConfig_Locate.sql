SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_SolutionConfig_Locate') IS NOT NULL DROP PROCEDURE dbo.stp_SolutionConfig_Locate
GO
CREATE PROCEDURE dbo.stp_SolutionConfig_Locate
    @prmKind VARCHAR(12),     -- environment | group | property
    @prmID   INT
AS

/*

TEST
----

    EXEC stp_SolutionConfig_Locate 'property', 1

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------------------------------------
09/21/2026  Felix        Story 3516 Solution configuration: which solution an environment, group or property belongs to -- asked
                         before every read or write so a scoped caller reaches only the solutions it was given. No row = it does
                         not exist.

*/

SET NOCOUNT ON

SET @prmKind = LOWER(LTRIM(RTRIM(ISNULL(@prmKind, ''))))

IF @prmKind = 'environment'
    SELECT ce.SolutionID FROM dbo.SolutionConfigEnvironment ce WHERE ce.SolutionConfigEnvironmentID = @prmID
ELSE IF @prmKind = 'group'
    SELECT g.SolutionID FROM dbo.SolutionConfigGroup g WHERE g.SolutionConfigGroupID = @prmID
ELSE IF @prmKind = 'property'
    SELECT g.SolutionID
    FROM   dbo.SolutionConfigProperty p JOIN dbo.SolutionConfigGroup g ON g.SolutionConfigGroupID = p.SolutionConfigGroupID
    WHERE  p.SolutionConfigPropertyID = @prmID
ELSE
    SELECT CAST(NULL AS INT) AS SolutionID WHERE 1 = 0

GO
