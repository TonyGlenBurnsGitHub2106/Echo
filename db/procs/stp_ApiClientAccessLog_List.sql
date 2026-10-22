SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_ApiClientAccessLog_List') IS NOT NULL DROP PROCEDURE dbo.stp_ApiClientAccessLog_List
GO
CREATE PROCEDURE dbo.stp_ApiClientAccessLog_List
    @prmApiClientID INT = NULL,
    @prmRefusedOnly BIT = 0,
    @prmOffset      INT = 0,
    @prmPageSize    INT = 50
AS

/*

TEST
----

    EXEC stp_ApiClientAccessLog_List

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------------------------------------
09/17/2026  Felix        Story 3493: one page of the client access log, newest first, optionally one client or refusals only.

*/

SET NOCOUNT ON

IF @prmPageSize IS NULL OR @prmPageSize <= 0 SET @prmPageSize = 50
IF @prmPageSize > 500 SET @prmPageSize = 500
IF @prmOffset   IS NULL OR @prmOffset   <  0 SET @prmOffset   = 0
IF ISNULL(@prmApiClientID, 0) <= 0 SET @prmApiClientID = NULL

SELECT ApiClientAccessLogID, ApiClientID, ClientCode, RequestPath, HttpMethod, StatusCode, IsAllowed, ClientIp, CreatedDate,
       (SELECT COUNT(*) FROM dbo.ApiClientAccessLog t
        WHERE (@prmApiClientID IS NULL OR t.ApiClientID = @prmApiClientID) AND (@prmRefusedOnly = 0 OR t.IsAllowed = 0)) AS TotalCount
FROM   dbo.ApiClientAccessLog
WHERE  (@prmApiClientID IS NULL OR ApiClientID = @prmApiClientID)
AND    (@prmRefusedOnly = 0 OR IsAllowed = 0)
ORDER BY ApiClientAccessLogID DESC
OFFSET @prmOffset ROWS FETCH NEXT @prmPageSize ROWS ONLY

GO
