SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.trg_SolutionStatus_Delete') IS NOT NULL DROP TRIGGER dbo.trg_SolutionStatus_Delete
GO
/*

Trigger trg_SolutionStatus_Delete on dbo.SolutionStatus.

VERSION HISTORY

Date        Developer    Description
----------  -----------  ----------------------------------------------------------
2026-09-17  Felix        Story 3493: audit row into SolutionStatus_LOG (the Solutions family).

*/

CREATE TRIGGER dbo.trg_SolutionStatus_Delete ON dbo.SolutionStatus AFTER DELETE AS
BEGIN SET NOCOUNT ON;
    INSERT INTO dbo.SolutionStatus_LOG (IsInsertion, DeletedDate, SolutionStatusID, StatusCode, StatusName, IsClosed, ColorHex, SortOrder, CreatedDate)
    SELECT 0, GETDATE(), SolutionStatusID, StatusCode, StatusName, IsClosed, ColorHex, SortOrder, CreatedDate FROM Deleted; END;

GO
