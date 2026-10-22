SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.trg_ApiClient_Insert') IS NOT NULL DROP TRIGGER dbo.trg_ApiClient_Insert
GO
/*

Trigger trg_ApiClient_Insert on dbo.ApiClient.

VERSION HISTORY

Date        Developer    Description
----------  -----------  ----------------------------------------------------------
2026-09-17  Felix        Story 3493: audit row into ApiClient_LOG (the Solutions family).

*/

CREATE TRIGGER dbo.trg_ApiClient_Insert ON dbo.ApiClient AFTER INSERT AS
BEGIN SET NOCOUNT ON;
    INSERT INTO dbo.ApiClient_LOG (IsInsertion, ApiClientID, ClientCode, ClientName, KeyHash, KeyHint, IsEnabled, AllowedPathPrefixes, ClientNotes, LastUsedDate, CreatedDate, ModifiedDate)
    SELECT 1, ApiClientID, ClientCode, ClientName, KeyHash, KeyHint, IsEnabled, AllowedPathPrefixes, ClientNotes, LastUsedDate, CreatedDate, ModifiedDate FROM Inserted; END;

GO
