SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.trg_SolutionApiClient_Insert') IS NOT NULL DROP TRIGGER dbo.trg_SolutionApiClient_Insert
GO
/*

Trigger trg_SolutionApiClient_Insert on dbo.SolutionApiClient.

VERSION HISTORY

Date        Developer    Description
----------  -----------  ----------------------------------------------------------
2026-09-17  Felix        Story 3493: audit row into SolutionApiClient_LOG (the Solutions family).

*/

CREATE TRIGGER dbo.trg_SolutionApiClient_Insert ON dbo.SolutionApiClient AFTER INSERT AS
BEGIN SET NOCOUNT ON;
    INSERT INTO dbo.SolutionApiClient_LOG (IsInsertion, SolutionApiClientID, SolutionID, ApiClientID, CreatedDate)
    SELECT 1, SolutionApiClientID, SolutionID, ApiClientID, CreatedDate FROM Inserted; END;

GO
