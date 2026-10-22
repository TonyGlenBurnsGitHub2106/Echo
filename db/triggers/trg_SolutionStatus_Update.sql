SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.trg_SolutionStatus_Update') IS NOT NULL DROP TRIGGER dbo.trg_SolutionStatus_Update
GO
/*

Trigger trg_SolutionStatus_Update on dbo.SolutionStatus.

VERSION HISTORY

Date        Developer    Description
----------  -----------  ----------------------------------------------------------
2026-09-17  Felix        Story 3493: audit row into SolutionStatus_LOG (the Solutions family).

*/

CREATE TRIGGER dbo.trg_SolutionStatus_Update ON dbo.SolutionStatus AFTER UPDATE AS
BEGIN SET NOCOUNT ON;
    INSERT INTO dbo.SolutionStatus_LOG (IsInsertion, SolutionStatusID, StatusCode, StatusName, IsClosed, ColorHex, SortOrder, CreatedDate)
    SELECT 0, SolutionStatusID, StatusCode, StatusName, IsClosed, ColorHex, SortOrder, CreatedDate FROM Inserted; END;

GO
