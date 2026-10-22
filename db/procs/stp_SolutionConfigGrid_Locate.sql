SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_SolutionConfigGrid_Locate') IS NOT NULL DROP PROCEDURE dbo.stp_SolutionConfigGrid_Locate
GO
CREATE PROCEDURE dbo.stp_SolutionConfigGrid_Locate
    @prmKind VARCHAR(12),     -- axis | axisvalue | column | group | property
    @prmID   INT
AS

/*

TEST
----

    EXEC stp_SolutionConfigGrid_Locate 'property', 1

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------------------------------------
10/09/2026  Felix        Story 5611 Solution Configuration II: which solution an axis, axis value, column, group or property
                         belongs to -- asked before every read or write so a scoped caller reaches only the solutions it was
                         given. No row = it does not exist.

*/

SET NOCOUNT ON

SET @prmKind = LOWER(LTRIM(RTRIM(ISNULL(@prmKind, ''))))

IF @prmKind = 'axis'
    SELECT a.SolutionID FROM dbo.SolutionConfigGridAxis a WHERE a.SolutionConfigGridAxisID = @prmID
ELSE IF @prmKind = 'axisvalue'
    SELECT a.SolutionID
    FROM   dbo.SolutionConfigGridAxisValue av JOIN dbo.SolutionConfigGridAxis a ON a.SolutionConfigGridAxisID = av.SolutionConfigGridAxisID
    WHERE  av.SolutionConfigGridAxisValueID = @prmID
ELSE IF @prmKind = 'column'
    SELECT c.SolutionID FROM dbo.SolutionConfigGridColumn c WHERE c.SolutionConfigGridColumnID = @prmID
ELSE IF @prmKind = 'group'
    SELECT g.SolutionID FROM dbo.SolutionConfigGridGroup g WHERE g.SolutionConfigGridGroupID = @prmID
ELSE IF @prmKind = 'property'
    SELECT p.SolutionID FROM dbo.SolutionConfigGridProperty p WHERE p.SolutionConfigGridPropertyID = @prmID
ELSE
    SELECT CAST(NULL AS INT) AS SolutionID WHERE 1 = 0

GO
