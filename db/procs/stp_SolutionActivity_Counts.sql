SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_SolutionActivity_Counts') IS NOT NULL DROP PROCEDURE dbo.stp_SolutionActivity_Counts
GO

CREATE PROCEDURE dbo.stp_SolutionActivity_Counts
    @prmSolutionID INT,
    @prmSince      DATETIME,
    @prmMemberCode VARCHAR(20) = NULL
AS

/*

TEST
----

    EXEC stp_SolutionActivity_Counts 6, '2026-10-20', 'tony'

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------------------------------------
11/02/2026  Felix        Since you were last here: the counts for the band under the SAME filter as
                         stp_SolutionActivity_List -- 'total', and 'kind' per kind. Bucket / BucketKey / Cnt.

*/

SET NOCOUNT ON

DECLARE @me VARCHAR(20) = LOWER(LTRIM(RTRIM(ISNULL(@prmMemberCode, ''))))

;WITH A AS (
    SELECT a.Kind FROM dbo.fn_SolutionActivity(@prmSolutionID, @prmSince) a
    WHERE  LOWER(ISNULL(a.Who, '')) <> @me OR @me = ''
)
SELECT 'total' AS Bucket, '' AS BucketKey, COUNT(*) AS Cnt FROM A
UNION ALL
SELECT 'kind', Kind, COUNT(*) FROM A GROUP BY Kind

GO
