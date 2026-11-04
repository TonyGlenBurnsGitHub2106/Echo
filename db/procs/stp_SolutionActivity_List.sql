SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_SolutionActivity_List') IS NOT NULL DROP PROCEDURE dbo.stp_SolutionActivity_List
GO

CREATE PROCEDURE dbo.stp_SolutionActivity_List
    @prmSolutionID INT,
    @prmSince      DATETIME,
    @prmMemberCode VARCHAR(20) = NULL,   -- this member's own changes are left out
    @prmOffset     INT = 0,
    @prmPageSize   INT = 50
AS

/*

TEST
----

    EXEC stp_SolutionActivity_List 6, '2026-10-20', 'tony', 0, 50

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------------------------------------
11/02/2026  Felix        Since you were last here: one page of what changed in a solution after @prmSince,
                         across every kind (fn_SolutionActivity), newest first, leaving out the caller's own
                         changes. Who carries the member's name for the band.

*/

SET NOCOUNT ON

DECLARE @size INT = CASE WHEN ISNULL(@prmPageSize, 0) BETWEEN 1 AND 500 THEN @prmPageSize ELSE 50 END
DECLARE @me VARCHAR(20) = LOWER(LTRIM(RTRIM(ISNULL(@prmMemberCode, ''))))

SELECT  a.Kind, a.RecordID, a.Title, a.Sub, a.Action, a.Who, m.MemberName AS WhoName, a.ChangedDate
FROM    dbo.fn_SolutionActivity(@prmSolutionID, @prmSince) a
LEFT JOIN dbo.SolutionMember m ON m.MemberCode = a.Who
WHERE   LOWER(ISNULL(a.Who, '')) <> @me OR @me = ''
ORDER BY a.ChangedDate DESC, a.Kind, a.RecordID
OFFSET ISNULL(@prmOffset, 0) ROWS FETCH NEXT @size ROWS ONLY

GO
