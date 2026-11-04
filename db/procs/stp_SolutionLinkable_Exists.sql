SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_SolutionLinkable_Exists') IS NOT NULL DROP PROCEDURE dbo.stp_SolutionLinkable_Exists
GO
CREATE PROCEDURE dbo.stp_SolutionLinkable_Exists
    @prmSolutionID INT,
    @prmKind       VARCHAR(20),
    @prmID         INT
AS

/*

TEST
----

    EXEC stp_SolutionLinkable_Exists 6, 'item', 1200

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------------------------------------
11/04/2026  Felix        Story 5695 (Echo): is this record one a link can reach in this solution (vw_SolutionLinkable)?
                         A new question or decision checks the links it asks for with this BEFORE it is saved, so a
                         bad link refuses it with nothing written. Returns Found (0 or 1).

*/

SET NOCOUNT ON

SELECT CAST(CASE WHEN EXISTS (SELECT 1 FROM dbo.vw_SolutionLinkable
                              WHERE SolutionID = @prmSolutionID AND Kind = LOWER(LTRIM(RTRIM(ISNULL(@prmKind, ''))))
                                AND RecordID = @prmID) THEN 1 ELSE 0 END AS INT) AS Found

GO
