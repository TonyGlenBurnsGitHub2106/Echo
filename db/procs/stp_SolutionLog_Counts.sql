SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_SolutionLog_Counts') IS NOT NULL DROP PROCEDURE dbo.stp_SolutionLog_Counts
GO
CREATE PROCEDURE dbo.stp_SolutionLog_Counts
    @prmSolutionID            INT,
    @prmSolutionProjectID     INT          = NULL,
    @prmSolutionProjectItemID INT          = NULL,
    @prmAuthorCode            VARCHAR(20)  = NULL,
    @prmLogTypeCode           VARCHAR(20)  = NULL,
    @prmSinceSequence         INT          = NULL,   -- only entries AFTER this sequence number
    @prmSearch                NVARCHAR(400) = NULL
AS

/*

TEST
----

    EXEC stp_SolutionLog_Counts 1

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------------------------------------
09/17/2026  Felix        Story 3493: how many journal entries match a filter, in total and per type and author. Same
                         filter block, same order, as stp_SolutionLog_List, so "showing 50 of 212" is honest.
                         Returns Bucket / BucketKey / Cnt rows.

*/

SET NOCOUNT ON

SET @prmAuthorCode  = NULLIF(LOWER(LTRIM(RTRIM(ISNULL(@prmAuthorCode, '')))), '')
SET @prmLogTypeCode = NULLIF(LOWER(LTRIM(RTRIM(ISNULL(@prmLogTypeCode, '')))), '')
SET @prmSearch      = NULLIF(LTRIM(RTRIM(ISNULL(@prmSearch, ''))), '')
IF ISNULL(@prmSolutionProjectID, 0)     <= 0 SET @prmSolutionProjectID = NULL
IF ISNULL(@prmSolutionProjectItemID, 0) <= 0 SET @prmSolutionProjectItemID = NULL
IF ISNULL(@prmSinceSequence, 0)         <= 0 SET @prmSinceSequence = NULL
SELECT 'total' AS Bucket, CAST('' AS VARCHAR(40)) AS BucketKey, COUNT(*) AS Cnt
FROM    dbo.SolutionLog l
JOIN    dbo.SolutionLogType lt ON lt.SolutionLogTypeID = l.SolutionLogTypeID
LEFT JOIN dbo.SolutionMember am ON am.SolutionMemberID = l.AuthorMemberID
LEFT JOIN dbo.SolutionProject p ON p.SolutionProjectID = l.SolutionProjectID
LEFT JOIN dbo.SolutionProjectItem i ON i.SolutionProjectItemID = l.SolutionProjectItemID
WHERE   l.SolutionID = @prmSolutionID
AND     (@prmSolutionProjectID     IS NULL OR l.SolutionProjectID     = @prmSolutionProjectID)
AND     (@prmSolutionProjectItemID IS NULL OR l.SolutionProjectItemID = @prmSolutionProjectItemID)
AND     (@prmAuthorCode            IS NULL OR am.MemberCode = @prmAuthorCode)
AND     (@prmLogTypeCode           IS NULL OR lt.LogTypeCode = @prmLogTypeCode)
AND     (@prmSinceSequence         IS NULL OR l.SequenceNumber > @prmSinceSequence)
AND     (@prmSearch                IS NULL OR l.LogText LIKE '%' + @prmSearch + '%')
UNION ALL
SELECT 'type', lt.LogTypeCode, COUNT(*)
FROM    dbo.SolutionLog l
JOIN    dbo.SolutionLogType lt ON lt.SolutionLogTypeID = l.SolutionLogTypeID
LEFT JOIN dbo.SolutionMember am ON am.SolutionMemberID = l.AuthorMemberID
LEFT JOIN dbo.SolutionProject p ON p.SolutionProjectID = l.SolutionProjectID
LEFT JOIN dbo.SolutionProjectItem i ON i.SolutionProjectItemID = l.SolutionProjectItemID
WHERE   l.SolutionID = @prmSolutionID
AND     (@prmSolutionProjectID     IS NULL OR l.SolutionProjectID     = @prmSolutionProjectID)
AND     (@prmSolutionProjectItemID IS NULL OR l.SolutionProjectItemID = @prmSolutionProjectItemID)
AND     (@prmAuthorCode            IS NULL OR am.MemberCode = @prmAuthorCode)
AND     (@prmLogTypeCode           IS NULL OR lt.LogTypeCode = @prmLogTypeCode)
AND     (@prmSinceSequence         IS NULL OR l.SequenceNumber > @prmSinceSequence)
AND     (@prmSearch                IS NULL OR l.LogText LIKE '%' + @prmSearch + '%')
GROUP BY lt.LogTypeCode
UNION ALL
SELECT 'author', ISNULL(am.MemberCode, 'unknown'), COUNT(*)
FROM    dbo.SolutionLog l
JOIN    dbo.SolutionLogType lt ON lt.SolutionLogTypeID = l.SolutionLogTypeID
LEFT JOIN dbo.SolutionMember am ON am.SolutionMemberID = l.AuthorMemberID
LEFT JOIN dbo.SolutionProject p ON p.SolutionProjectID = l.SolutionProjectID
LEFT JOIN dbo.SolutionProjectItem i ON i.SolutionProjectItemID = l.SolutionProjectItemID
WHERE   l.SolutionID = @prmSolutionID
AND     (@prmSolutionProjectID     IS NULL OR l.SolutionProjectID     = @prmSolutionProjectID)
AND     (@prmSolutionProjectItemID IS NULL OR l.SolutionProjectItemID = @prmSolutionProjectItemID)
AND     (@prmAuthorCode            IS NULL OR am.MemberCode = @prmAuthorCode)
AND     (@prmLogTypeCode           IS NULL OR lt.LogTypeCode = @prmLogTypeCode)
AND     (@prmSinceSequence         IS NULL OR l.SequenceNumber > @prmSinceSequence)
AND     (@prmSearch                IS NULL OR l.LogText LIKE '%' + @prmSearch + '%')
GROUP BY ISNULL(am.MemberCode, 'unknown')

GO
