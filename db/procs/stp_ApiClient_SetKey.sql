SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_ApiClient_SetKey') IS NOT NULL DROP PROCEDURE dbo.stp_ApiClient_SetKey
GO
CREATE PROCEDURE dbo.stp_ApiClient_SetKey
    @prmApiClientID INT,
    @prmKeyHash     CHAR(64),
    @prmKeyHint     VARCHAR(8)
AS

/*

TEST
----

    EXEC stp_ApiClient_SetKey 0, NULL, NULL

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------------------------------------
09/17/2026  Felix        Story 3493: store the SHA-256 hash of a freshly issued key (or NULL to revoke it). The key itself
                         never reaches the database. Returns RowsAffected.

*/

SET NOCOUNT ON

UPDATE dbo.ApiClient
SET    KeyHash = NULLIF(LOWER(LTRIM(RTRIM(ISNULL(@prmKeyHash, '')))), ''), KeyHint = NULLIF(LTRIM(RTRIM(ISNULL(@prmKeyHint, ''))), ''),
       ModifiedDate = GETDATE()
WHERE  ApiClientID = @prmApiClientID
SELECT @@ROWCOUNT AS RowsAffected

GO
