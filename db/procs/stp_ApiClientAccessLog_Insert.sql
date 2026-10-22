SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_ApiClientAccessLog_Insert') IS NOT NULL DROP PROCEDURE dbo.stp_ApiClientAccessLog_Insert
GO
CREATE PROCEDURE dbo.stp_ApiClientAccessLog_Insert
    @prmApiClientID INT,
    @prmClientCode  VARCHAR(40),
    @prmRequestPath NVARCHAR(500),
    @prmHttpMethod  VARCHAR(10),
    @prmStatusCode  INT,
    @prmIsAllowed   BIT,
    @prmClientIp    VARCHAR(64)
AS

/*

TEST
----

    EXEC stp_ApiClientAccessLog_Insert NULL, 'nobody', '/api/solutions', 'GET', 401, 0, '127.0.0.1'

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------------------------------------
09/17/2026  Felix        Story 3493: record one request made with API client headers -- allowed or refused, known client
                         or not -- and stamp the client's LastUsedDate when it was a real one. Returns nothing.

*/

SET NOCOUNT ON

IF ISNULL(@prmApiClientID, 0) <= 0 SET @prmApiClientID = NULL

INSERT INTO dbo.ApiClientAccessLog (ApiClientID, ClientCode, RequestPath, HttpMethod, StatusCode, IsAllowed, ClientIp)
VALUES (@prmApiClientID, LEFT(ISNULL(@prmClientCode, ''), 40), LEFT(ISNULL(@prmRequestPath, ''), 500), LEFT(ISNULL(@prmHttpMethod, ''), 10),
        ISNULL(@prmStatusCode, 0), ISNULL(@prmIsAllowed, 0), LEFT(@prmClientIp, 64))

IF @prmApiClientID IS NOT NULL
    UPDATE dbo.ApiClient SET LastUsedDate = GETDATE() WHERE ApiClientID = @prmApiClientID

GO
