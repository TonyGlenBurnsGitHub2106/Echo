SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_SolutionLog_List') IS NOT NULL DROP PROCEDURE dbo.stp_SolutionLog_List
GO
CREATE PROCEDURE dbo.stp_SolutionLog_List
    @prmSolutionID            INT,
    @prmSolutionProjectID     INT          = NULL,
    @prmSolutionProjectItemID INT          = NULL,
    @prmAuthorCode            VARCHAR(20)  = NULL,
    @prmLogTypeCode           VARCHAR(20)  = NULL,
    @prmSinceSequence         INT          = NULL,   -- only entries AFTER this sequence number
    @prmSearch                NVARCHAR(400) = NULL,
    @prmAscending             BIT = 0,               -- 1 = oldest first (the order things were done)
    @prmOffset                INT = 0,
    @prmPageSize              INT = 50
AS

/*

TEST
----

    EXEC stp_SolutionLog_List 1

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------------------------------------
09/17/2026  Felix        Story 3493: one page of a solution's journal. Filters, ordering and paging happen here so page 2
                         continues page 1. Never returns the whole NVARCHAR(MAX) entry: LogText is capped at
                         2,000 characters with IsTruncated set, and the full entry comes from stp_SolutionLog_Get.
                         The filter block (first seven parameters) is shared with stp_SolutionLog_Counts.

09/22/2026  Felix        Solutions documents are now NOTES (SolutionDocument* -> SolutionNote*, Tony 09/22/2026).

*/

SET NOCOUNT ON

SET @prmAuthorCode  = NULLIF(LOWER(LTRIM(RTRIM(ISNULL(@prmAuthorCode, '')))), '')
SET @prmLogTypeCode = NULLIF(LOWER(LTRIM(RTRIM(ISNULL(@prmLogTypeCode, '')))), '')
SET @prmSearch      = NULLIF(LTRIM(RTRIM(ISNULL(@prmSearch, ''))), '')
IF ISNULL(@prmSolutionProjectID, 0)     <= 0 SET @prmSolutionProjectID = NULL
IF ISNULL(@prmSolutionProjectItemID, 0) <= 0 SET @prmSolutionProjectItemID = NULL
IF ISNULL(@prmSinceSequence, 0)         <= 0 SET @prmSinceSequence = NULL
IF @prmPageSize IS NULL OR @prmPageSize <= 0 SET @prmPageSize = 50
IF @prmPageSize > 500 SET @prmPageSize = 500
IF @prmOffset   IS NULL OR @prmOffset   <  0 SET @prmOffset   = 0

SELECT  l.SolutionLogID, l.SolutionID, l.SolutionProjectID, p.ProjectName, l.SolutionProjectItemID, i.ItemTitle,
        l.SequenceNumber, l.SolutionLogTypeID, lt.LogTypeCode, lt.LogTypeName,
        l.AuthorMemberID, am.MemberCode AS AuthorCode, am.MemberName AS AuthorName,
        CAST(LEFT(l.LogText, 2000) AS NVARCHAR(2000)) AS LogText,
        CASE WHEN LEN(l.LogText) > 2000 THEN 1 ELSE 0 END AS IsTruncated,
        l.LogDate, l.CreatedDate, l.ModifiedDate,
        (SELECT COUNT(*) FROM dbo.SolutionNoteStep s WHERE s.SourceSolutionLogID = l.SolutionLogID) AS StepCount
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
ORDER BY
        CASE WHEN @prmAscending = 1 THEN l.SequenceNumber END ASC,
        CASE WHEN @prmAscending = 0 THEN l.SequenceNumber END DESC
OFFSET @prmOffset ROWS FETCH NEXT @prmPageSize ROWS ONLY


GO
