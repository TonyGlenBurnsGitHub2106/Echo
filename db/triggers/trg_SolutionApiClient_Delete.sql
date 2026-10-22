SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.trg_SolutionApiClient_Delete') IS NOT NULL DROP TRIGGER dbo.trg_SolutionApiClient_Delete
GO
/*

Trigger trg_SolutionApiClient_Delete on dbo.SolutionApiClient.

VERSION HISTORY

Date        Developer    Description
----------  -----------  ----------------------------------------------------------
2026-09-17  Felix        Story 3493: audit row into SolutionApiClient_LOG (the Solutions family).

*/

CREATE TRIGGER dbo.trg_SolutionApiClient_Delete ON dbo.SolutionApiClient AFTER DELETE AS
BEGIN SET NOCOUNT ON;
    INSERT INTO dbo.SolutionApiClient_LOG (IsInsertion, DeletedDate, SolutionApiClientID, SolutionID, ApiClientID, CreatedDate)
    SELECT 0, GETDATE(), SolutionApiClientID, SolutionID, ApiClientID, CreatedDate FROM Deleted; END;

GO
