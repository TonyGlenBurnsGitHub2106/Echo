SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.trg_SolutionApiClient_Update') IS NOT NULL DROP TRIGGER dbo.trg_SolutionApiClient_Update
GO
/*

Trigger trg_SolutionApiClient_Update on dbo.SolutionApiClient.

VERSION HISTORY

Date        Developer    Description
----------  -----------  ----------------------------------------------------------
2026-09-17  Felix        Story 3493: audit row into SolutionApiClient_LOG (the Solutions family).

*/

CREATE TRIGGER dbo.trg_SolutionApiClient_Update ON dbo.SolutionApiClient AFTER UPDATE AS
BEGIN SET NOCOUNT ON;
    INSERT INTO dbo.SolutionApiClient_LOG (IsInsertion, SolutionApiClientID, SolutionID, ApiClientID, CreatedDate)
    SELECT 0, SolutionApiClientID, SolutionID, ApiClientID, CreatedDate FROM Inserted; END;

GO
