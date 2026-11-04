SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_Solution_StatusSummaryAge') IS NOT NULL DROP PROCEDURE dbo.stp_Solution_StatusSummaryAge
GO
CREATE PROCEDURE dbo.stp_Solution_StatusSummaryAge
    @prmSolutionID INT
AS

/*

TEST
----

    EXEC stp_Solution_StatusSummaryAge 6

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------------------------------------
11/04/2026  Felix        Story 5695 (Echo): when "Where things stand" was last WRITTEN -- not when the solution was
                         last saved. Read from Solution_LOG, which keeps every saved version: the first version
                         holding today's text after the last one that held different text. So it needs no new
                         column and is right for summaries written before it existed (Claims' was written on
                         09/17 and still said so on 11/02). A solution whose history is gone (copied in from
                         elsewhere) is no newer than the last save it came with. Only history after the id was last deleted
                         counts (a copy keeps its ids). WrittenDate is NULL when there is no
                         summary. One row: WrittenDate, AgeDays.

*/

SET NOCOUNT ON

DECLARE @current NVARCHAR(4000), @fallback DATETIME
SELECT @current = NULLIF(LTRIM(RTRIM(ISNULL(StatusSummary, N''))), N''), @fallback = ISNULL(ModifiedDate, CreatedDate)
FROM   dbo.Solution WHERE SolutionID = @prmSolutionID

DECLARE @written DATETIME = NULL
-- A solution id can come back (a copy keeps its ids): only the history after it was last deleted counts.
DECLARE @reborn DATETIME = ISNULL((SELECT MAX(LogCreatedDate) FROM dbo.Solution_LOG
                                   WHERE SolutionID = @prmSolutionID AND DeletedDate IS NOT NULL), '19000101')
IF @current IS NOT NULL
BEGIN
    -- A version with no summary, or another one, is "different text" (@current is never blank here).
    DECLARE @lastOther DATETIME = (SELECT MAX(LogCreatedDate) FROM dbo.Solution_LOG
                                   WHERE SolutionID = @prmSolutionID AND DeletedDate IS NULL AND LogCreatedDate > @reborn
                                     AND LTRIM(RTRIM(ISNULL(StatusSummary, N''))) <> @current)
    SET @written = (SELECT MIN(LogCreatedDate) FROM dbo.Solution_LOG
                    WHERE SolutionID = @prmSolutionID AND DeletedDate IS NULL AND LogCreatedDate > @reborn
                      AND LTRIM(RTRIM(ISNULL(StatusSummary, N''))) = @current
                      AND (@lastOther IS NULL OR LogCreatedDate > @lastOther))
    -- A solution copied in arrives with its summary and no history of it: then it is no newer than the last
    -- save it came with (its ModifiedDate travels with it).
    IF @lastOther IS NULL AND @fallback < ISNULL(@written, GETDATE()) SET @written = @fallback
    IF @written IS NULL SET @written = @fallback
END

SELECT @written AS WrittenDate,
       CASE WHEN @written IS NULL THEN NULL ELSE DATEDIFF(DAY, @written, GETDATE()) END AS AgeDays

GO
