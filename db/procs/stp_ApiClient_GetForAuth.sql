SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_ApiClient_GetForAuth') IS NOT NULL DROP PROCEDURE dbo.stp_ApiClient_GetForAuth
GO
CREATE PROCEDURE dbo.stp_ApiClient_GetForAuth
    @prmClientCode VARCHAR(40)
AS

/*

TEST
----

    EXEC stp_ApiClient_GetForAuth 'rex'

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------------------------------------
09/17/2026  Felix        Story 3493: the one row the authentication filter needs to check a client's key: the hash, the
                         enabled flag and the path allowlist. Used by nothing else.

*/

SET NOCOUNT ON

SELECT ApiClientID, ClientCode, ClientName, KeyHash, IsEnabled, AllowedPathPrefixes
FROM   dbo.ApiClient
WHERE  ClientCode = LOWER(LTRIM(RTRIM(ISNULL(@prmClientCode, ''))))

GO
