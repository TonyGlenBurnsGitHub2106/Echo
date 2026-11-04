SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_SolutionActivity_Feed') IS NOT NULL DROP PROCEDURE dbo.stp_SolutionActivity_Feed
GO
CREATE PROCEDURE dbo.stp_SolutionActivity_Feed
    @prmSolutionID INT,
    @prmSince      DATETIME     = NULL,   -- NULL = the last 30 days
    @prmWho        VARCHAR(20)  = NULL,   -- one member code; NULL = everyone
    @prmKind       VARCHAR(10)  = NULL,   -- item | log | note | file | message | term | topic | question | decision
    @prmTopicID    INT          = NULL,   -- only records linked to this topic
    @prmOffset     INT          = 0,
    @prmPageSize   INT          = 50
AS

/*

TEST
----

    EXEC stp_SolutionActivity_Feed 6, NULL, NULL, NULL, NULL, 0, 50
    EXEC stp_SolutionActivity_Feed 6, '20261001', 'rex', 'log', NULL, 0, 50

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------------------------------------
11/04/2026  Felix        Story 5695 (Echo): the activity feed -- every change to a solution after a moment, EVERYONE's
                         (the member's own too, unlike Since you were last here), newest first, in one shape
                         (fn_SolutionActivity), narrowed by who, by kind, and by topic (records linked to it). From the
                         deep review: "what happened this week ... it is what the weekly logs were trying to be."
                         TotalCount is everything that matches, whatever page this is.

*/

SET NOCOUNT ON

DECLARE @since DATETIME = ISNULL(@prmSince, DATEADD(DAY, -30, GETDATE()))
DECLARE @who VARCHAR(20) = NULLIF(LOWER(LTRIM(RTRIM(ISNULL(@prmWho, '')))), '')
DECLARE @kind VARCHAR(10) = NULLIF(LOWER(LTRIM(RTRIM(ISNULL(@prmKind, '')))), '')
DECLARE @topic INT = NULLIF(ISNULL(@prmTopicID, 0), 0)
DECLARE @size INT = CASE WHEN ISNULL(@prmPageSize, 0) BETWEEN 1 AND 500 THEN @prmPageSize ELSE 50 END

SELECT  a.Kind, a.RecordID, a.Title, a.Sub, a.Action, a.Who, m.MemberName AS WhoName, a.ChangedDate,
        COUNT(*) OVER () AS TotalCount
FROM    dbo.fn_SolutionActivity(@prmSolutionID, @since) a
LEFT JOIN dbo.SolutionMember m ON m.MemberCode = a.Who
WHERE   (@who IS NULL OR a.Who = @who)
  AND   (@kind IS NULL OR a.Kind = @kind)
  AND   (@topic IS NULL OR (a.Kind = 'topic' AND a.RecordID = @topic) OR EXISTS (
            SELECT 1 FROM dbo.SolutionLink k
            WHERE  k.SolutionID = @prmSolutionID
              AND  ((k.FromKind = 'topic' AND k.FromID = @topic AND k.ToKind = a.Kind AND k.ToID = a.RecordID)
                 OR (k.ToKind = 'topic' AND k.ToID = @topic AND k.FromKind = a.Kind AND k.FromID = a.RecordID))))
ORDER BY a.ChangedDate DESC, a.Kind, a.RecordID
OFFSET ISNULL(@prmOffset, 0) ROWS FETCH NEXT @size ROWS ONLY

GO
