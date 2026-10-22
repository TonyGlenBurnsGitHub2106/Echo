SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.trg_SolutionStatus_Insert') IS NOT NULL DROP TRIGGER dbo.trg_SolutionStatus_Insert
GO
/*

Trigger trg_SolutionStatus_Insert on dbo.SolutionStatus.

VERSION HISTORY

Date        Developer    Description
----------  -----------  ----------------------------------------------------------
2026-09-17  Felix        Story 3493: audit row into SolutionStatus_LOG (the Solutions family).

*/

CREATE TRIGGER dbo.trg_SolutionStatus_Insert ON dbo.SolutionStatus AFTER INSERT AS
BEGIN SET NOCOUNT ON;
    INSERT INTO dbo.SolutionStatus_LOG (IsInsertion, SolutionStatusID, StatusCode, StatusName, IsClosed, ColorHex, SortOrder, CreatedDate)
    SELECT 1, SolutionStatusID, StatusCode, StatusName, IsClosed, ColorHex, SortOrder, CreatedDate FROM Inserted; END;

GO
