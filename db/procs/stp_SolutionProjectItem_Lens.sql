SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_SolutionProjectItem_Lens') IS NOT NULL DROP PROCEDURE dbo.stp_SolutionProjectItem_Lens
GO
CREATE PROCEDURE dbo.stp_SolutionProjectItem_Lens
    @prmSolutionID  INT,
    @prmMemberCode  VARCHAR(20)  = NULL,   -- whose Now this is: their items come first
    @prmBucket      VARCHAR(10)  = NULL,   -- now | next | waiting; NULL = the first page of each
    @prmOffset      INT          = 0,      -- within @prmBucket
    @prmPageSize    INT          = 50,
    @prmSoonDays    INT          = 7,      -- due within this many days counts as Now
    @prmIdleDays    INT          = 7       -- a Now item untouched this long is idle
AS

/*

TEST
----

    EXEC stp_SolutionProjectItem_Lens 6, 'tony', NULL, 0, 50, 7, 7
    EXEC stp_SolutionProjectItem_Lens 6, 'tony', 'next', 50, 50, 7, 7

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------------------------------------
11/04/2026  Felix        Story 5695 (Echo): the Now / Next / Waiting lens over a solution's OPEN items. From the deep
                         review of 11/02: "0 due dates, 326 items unassigned, 62 percent at the default priority ...
                         a ten-day silence that nothing flagged." Every open item lands in one bucket, decided here:
                           waiting  blocked, or it waits on an item that is still open
                           now      in progress, or due within @prmSoonDays (overdue too)
                           next     everything else still open
                         Within a bucket: Now has the overdue first, then by due date; then, in every bucket, the
                         member's own first, then by priority, project and order. IdleDays says how long since the item changed; a Now
                         item idle @prmIdleDays or more is flagged (IsIdle) -- the weekly review. TotalInBucket is the
                         whole bucket, whatever page this is.

*/

SET NOCOUNT ON

DECLARE @me INT = (SELECT SolutionMemberID FROM dbo.SolutionMember WHERE MemberCode = LOWER(LTRIM(RTRIM(ISNULL(@prmMemberCode, '')))))
DECLARE @bucket VARCHAR(10) = NULLIF(LOWER(LTRIM(RTRIM(ISNULL(@prmBucket, '')))), '')
DECLARE @size INT = CASE WHEN ISNULL(@prmPageSize, 0) BETWEEN 1 AND 500 THEN @prmPageSize ELSE 50 END
DECLARE @soon DATETIME = DATEADD(DAY, ISNULL(@prmSoonDays, 7) + 1, CAST(CAST(GETDATE() AS DATE) AS DATETIME))

;WITH OpenItems AS (
    SELECT  i.SolutionProjectItemID, i.ItemTitle, i.ParentItemID, i.Priority, i.SortOrder, i.DueDate,
            i.AssigneeMemberID, p.SolutionProjectID, p.ProjectName, p.SortOrder AS ProjectSort,
            s.StatusCode, s.StatusName, s.ColorHex,
            ISNULL(i.ModifiedDate, i.CreatedDate) AS TouchedDate,
            (SELECT COUNT(*) FROM dbo.SolutionItemDependency d
             JOIN dbo.SolutionProjectItem o ON o.SolutionProjectItemID = d.DependsOnSolutionProjectItemID
             JOIN dbo.SolutionStatus os ON os.SolutionStatusID = o.SolutionStatusID
             WHERE d.SolutionProjectItemID = i.SolutionProjectItemID AND os.IsClosed = 0) AS OpenWaits
    FROM    dbo.SolutionProjectItem i
    JOIN    dbo.SolutionProject p ON p.SolutionProjectID = i.SolutionProjectID
    JOIN    dbo.SolutionStatus s ON s.SolutionStatusID = i.SolutionStatusID
    WHERE   p.SolutionID = @prmSolutionID AND s.IsClosed = 0
), Placed AS (
    SELECT  o.*,
            CAST(CASE WHEN o.StatusCode = 'blocked' OR o.OpenWaits > 0 THEN 'waiting'
                      WHEN o.StatusCode = 'inprogress' OR (o.DueDate IS NOT NULL AND o.DueDate < @soon) THEN 'now'
                      ELSE 'next' END AS VARCHAR(10)) AS Bucket,
            DATEDIFF(DAY, o.TouchedDate, GETDATE()) AS IdleDays,
            CAST(CASE WHEN @me IS NOT NULL AND o.AssigneeMemberID = @me THEN 1 ELSE 0 END AS BIT) AS IsMine,
            CAST(CASE WHEN o.DueDate IS NOT NULL AND o.DueDate < CAST(CAST(GETDATE() AS DATE) AS DATETIME) THEN 1 ELSE 0 END AS BIT) AS IsOverdue
    FROM    OpenItems o
), Ranked AS (
    SELECT  p.*,
            ROW_NUMBER() OVER (PARTITION BY p.Bucket ORDER BY
                CASE WHEN p.Bucket = 'now' THEN CASE WHEN p.IsOverdue = 1 THEN 0 ELSE 1 END ELSE 0 END,
                CASE WHEN p.Bucket = 'now' THEN ISNULL(p.DueDate, '99991231') ELSE '99991231' END,
                CASE WHEN p.IsMine = 1 THEN 0 ELSE 1 END,
                p.Priority, p.ProjectSort, p.SortOrder, p.SolutionProjectItemID) AS RowInBucket,
            COUNT(*) OVER (PARTITION BY p.Bucket) AS TotalInBucket
    FROM    Placed p
)
SELECT  r.SolutionProjectItemID, r.ItemTitle, r.ParentItemID, r.SolutionProjectID, r.ProjectName, r.StatusCode, r.StatusName, r.ColorHex,
        r.Priority, r.DueDate, m.MemberCode AS AssigneeCode, m.MemberName AS AssigneeName,
        r.Bucket, r.IdleDays, CAST(CASE WHEN r.Bucket = 'now' AND r.IdleDays >= ISNULL(@prmIdleDays, 7) THEN 1 ELSE 0 END AS BIT) AS IsIdle,
        r.IsMine, r.IsOverdue, r.OpenWaits, r.RowInBucket, r.TotalInBucket
FROM    Ranked r
LEFT JOIN dbo.SolutionMember m ON m.SolutionMemberID = r.AssigneeMemberID
WHERE   (@bucket IS NULL AND r.RowInBucket <= @size)
     OR (@bucket IS NOT NULL AND r.Bucket = @bucket AND r.RowInBucket > ISNULL(@prmOffset, 0) AND r.RowInBucket <= ISNULL(@prmOffset, 0) + @size)
ORDER BY CASE r.Bucket WHEN 'now' THEN 0 WHEN 'waiting' THEN 1 ELSE 2 END, r.RowInBucket

GO
