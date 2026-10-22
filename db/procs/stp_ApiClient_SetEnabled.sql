SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_ApiClient_SetEnabled') IS NOT NULL DROP PROCEDURE dbo.stp_ApiClient_SetEnabled
GO
CREATE PROCEDURE dbo.stp_ApiClient_SetEnabled
    @prmApiClientID INT,
    @prmIsEnabled   BIT
AS

/*

TEST
----

    EXEC stp_ApiClient_SetEnabled 0, 0

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------------------------------------
09/17/2026  Felix        Story 3493: the kill switch: turn one client on or off without touching its key. Returns RowsAffected.

*/

SET NOCOUNT ON

UPDATE dbo.ApiClient SET IsEnabled = ISNULL(@prmIsEnabled, 0), ModifiedDate = GETDATE() WHERE ApiClientID = @prmApiClientID
SELECT @@ROWCOUNT AS RowsAffected

GO
