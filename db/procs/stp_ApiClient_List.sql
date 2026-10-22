SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_ApiClient_List') IS NOT NULL DROP PROCEDURE dbo.stp_ApiClient_List
GO
CREATE PROCEDURE dbo.stp_ApiClient_List
AS

/*

TEST
----

    EXEC stp_ApiClient_List

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------------------------------------
09/17/2026  Felix        Story 3493: every API client with how many solutions it may see and how busy it has been. The
                         key hash is never returned -- only KeyHint (the last four characters) and whether a key is set.

*/

SET NOCOUNT ON

SELECT  c.ApiClientID, c.ClientCode, c.ClientName, c.KeyHint,
        CASE WHEN c.KeyHash IS NULL THEN 0 ELSE 1 END AS HasKey,
        c.IsEnabled, c.AllowedPathPrefixes, c.ClientNotes, c.LastUsedDate, c.CreatedDate, c.ModifiedDate,
        (SELECT COUNT(*) FROM dbo.SolutionApiClient x WHERE x.ApiClientID = c.ApiClientID) AS SolutionCount,
        (SELECT COUNT(*) FROM dbo.ApiClientAccessLog a WHERE a.ApiClientID = c.ApiClientID AND a.CreatedDate >= DATEADD(DAY, -1, GETDATE())) AS RequestsLastDay,
        (SELECT COUNT(*) FROM dbo.ApiClientAccessLog a WHERE a.ApiClientID = c.ApiClientID AND a.IsAllowed = 0 AND a.CreatedDate >= DATEADD(DAY, -1, GETDATE())) AS RefusedLastDay
FROM    dbo.ApiClient c
ORDER BY c.ClientName

GO
