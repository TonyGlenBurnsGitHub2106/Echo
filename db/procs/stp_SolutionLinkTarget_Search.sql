SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_SolutionLinkTarget_Search') IS NOT NULL DROP PROCEDURE dbo.stp_SolutionLinkTarget_Search
GO

CREATE PROCEDURE dbo.stp_SolutionLinkTarget_Search
    @prmSolutionID INT,
    @prmSearch     NVARCHAR(200) = NULL,
    @prmKind       VARCHAR(20)   = NULL,    -- the record being linked FROM (left out of the answer)
    @prmID         INT           = NULL,
    @prmPageSize   INT           = 30,
    @prmTargetKind VARCHAR(20)   = NULL     -- Story 5695 (Echo): only records of this kind (e.g. items to wait on)
AS

/*

TEST
----

    EXEC stp_SolutionLinkTarget_Search 6, N'charset', 'item', 1200, 30

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------------------------------------
11/02/2026  Felix        Solution links: the Link picker. Records of one solution whose title (for a log entry,
                         "#<number> <first words>") holds the words typed, best first: a title that starts with
                         them, then topics, then the newest. With nothing typed, the topics and the newest
                         records. AlreadyLinked marks the ones joined to the record being linked from.

*/

SET NOCOUNT ON

DECLARE @q NVARCHAR(200) = LTRIM(RTRIM(ISNULL(@prmSearch, N'')))
DECLARE @kind VARCHAR(20) = LOWER(LTRIM(RTRIM(ISNULL(@prmKind, ''))))
DECLARE @id INT = ISNULL(@prmID, 0)
DECLARE @size INT = CASE WHEN ISNULL(@prmPageSize, 0) BETWEEN 1 AND 100 THEN @prmPageSize ELSE 30 END
-- LIKE wildcards in what was typed are matched as themselves.
DECLARE @like NVARCHAR(400) = REPLACE(REPLACE(REPLACE(@q, N'[', N'[[]'), N'%', N'[%]'), N'_', N'[_]')

SELECT TOP (@size) v.Kind, v.RecordID, v.Title, v.Sub, v.RecordDate, v.IsClosed,
       CAST(CASE WHEN EXISTS (SELECT 1 FROM dbo.SolutionLink k
                              WHERE k.SolutionID = @prmSolutionID
                                AND ((k.FromKind = @kind AND k.FromID = @id AND k.ToKind = v.Kind AND k.ToID = v.RecordID)
                                  OR (k.ToKind = @kind AND k.ToID = @id AND k.FromKind = v.Kind AND k.FromID = v.RecordID)))
                 THEN 1 ELSE 0 END AS BIT) AS AlreadyLinked
FROM   dbo.vw_SolutionLinkable v
WHERE  v.SolutionID = @prmSolutionID
  AND  NOT (v.Kind = @kind AND v.RecordID = @id)
  AND  (@q = N'' OR v.Title LIKE N'%' + @like + N'%')
  AND  (NULLIF(@prmTargetKind, '') IS NULL OR v.Kind = LOWER(@prmTargetKind))
ORDER BY CASE WHEN @q <> N'' AND v.Title LIKE @like + N'%' THEN 0 ELSE 1 END,
         CASE WHEN v.Kind = 'topic' THEN 0 ELSE 1 END,
         v.RecordDate DESC, v.Kind, v.RecordID

GO
