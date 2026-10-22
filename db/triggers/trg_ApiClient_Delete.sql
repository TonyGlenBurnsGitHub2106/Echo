SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.trg_ApiClient_Delete') IS NOT NULL DROP TRIGGER dbo.trg_ApiClient_Delete
GO
/*

Trigger trg_ApiClient_Delete on dbo.ApiClient.

VERSION HISTORY

Date        Developer    Description
----------  -----------  ----------------------------------------------------------
2026-09-17  Felix        Story 3493: audit row into ApiClient_LOG (the Solutions family).

*/

CREATE TRIGGER dbo.trg_ApiClient_Delete ON dbo.ApiClient AFTER DELETE AS
BEGIN SET NOCOUNT ON;
    INSERT INTO dbo.ApiClient_LOG (IsInsertion, DeletedDate, ApiClientID, ClientCode, ClientName, KeyHash, KeyHint, IsEnabled, AllowedPathPrefixes, ClientNotes, LastUsedDate, CreatedDate, ModifiedDate)
    SELECT 0, GETDATE(), ApiClientID, ClientCode, ClientName, KeyHash, KeyHint, IsEnabled, AllowedPathPrefixes, ClientNotes, LastUsedDate, CreatedDate, ModifiedDate FROM Deleted; END;

GO
