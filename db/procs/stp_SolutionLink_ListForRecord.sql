SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_SolutionLink_ListForRecord') IS NOT NULL DROP PROCEDURE dbo.stp_SolutionLink_ListForRecord
GO

CREATE PROCEDURE dbo.stp_SolutionLink_ListForRecord
    @prmSolutionID INT,
    @prmKind       VARCHAR(20),
    @prmID         INT,
    @prmOffset     INT = 0,
    @prmPageSize   INT = 50
AS

/*

TEST
----

    EXEC stp_SolutionLink_ListForRecord 6, 'topic', 1, 0, 50

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------------------------------------
11/02/2026  Felix        Solution links: everything linked to one record. A link has no direction, so these are
                         also its backlinks. Each row is the OTHER end -- kind, id, title and second line from
                         vw_SolutionLinkable. Topics first, then newest first. Paged in SQL; TotalCount is every
                         link of the record, whatever page this is.

*/

SET NOCOUNT ON

DECLARE @kind VARCHAR(20) = LOWER(LTRIM(RTRIM(ISNULL(@prmKind, ''))))
DECLARE @size INT = CASE WHEN ISNULL(@prmPageSize, 0) BETWEEN 1 AND 500 THEN @prmPageSize ELSE 50 END

;WITH Ends AS (
    SELECT k.SolutionLinkID, k.ToKind AS Kind, k.ToID AS RecordID, k.CreatedByMemberID, k.CreatedDate AS LinkedDate
    FROM   dbo.SolutionLink k
    WHERE  k.SolutionID = @prmSolutionID AND k.FromKind = @kind AND k.FromID = @prmID
    UNION ALL
    SELECT k.SolutionLinkID, k.FromKind, k.FromID, k.CreatedByMemberID, k.CreatedDate
    FROM   dbo.SolutionLink k
    WHERE  k.SolutionID = @prmSolutionID AND k.ToKind = @kind AND k.ToID = @prmID
)
SELECT  e.SolutionLinkID, e.Kind, e.RecordID, v.Title, v.Sub, v.RecordDate, v.IsClosed,
        e.LinkedDate, m.MemberCode AS LinkedByCode, m.MemberName AS LinkedByName,
        COUNT(*) OVER () AS TotalCount
FROM    Ends e
JOIN    dbo.vw_SolutionLinkable v ON v.SolutionID = @prmSolutionID AND v.Kind = e.Kind AND v.RecordID = e.RecordID
LEFT JOIN dbo.SolutionMember m ON m.SolutionMemberID = e.CreatedByMemberID
-- Story 5695 (Echo): after the topics, the decisions (so a topic opens with today's answer near the top), then the questions.
ORDER BY CASE e.Kind WHEN 'topic' THEN 0 WHEN 'decision' THEN 1 WHEN 'question' THEN 2 ELSE 3 END, v.RecordDate DESC, e.SolutionLinkID DESC
OFFSET ISNULL(@prmOffset, 0) ROWS FETCH NEXT @size ROWS ONLY

GO
