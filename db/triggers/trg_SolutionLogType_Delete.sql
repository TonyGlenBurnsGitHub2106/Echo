SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.trg_SolutionLogType_Delete') IS NOT NULL DROP TRIGGER dbo.trg_SolutionLogType_Delete
GO
/*

Trigger trg_SolutionLogType_Delete on dbo.SolutionLogType.

VERSION HISTORY

Date        Developer    Description
----------  -----------  ----------------------------------------------------------
2026-09-17  Felix        Story 3493: audit row into SolutionLogType_LOG (the Solutions family).

*/

CREATE TRIGGER dbo.trg_SolutionLogType_Delete ON dbo.SolutionLogType AFTER DELETE AS
BEGIN SET NOCOUNT ON;
    INSERT INTO dbo.SolutionLogType_LOG (IsInsertion, DeletedDate, SolutionLogTypeID, LogTypeCode, LogTypeName, SortOrder, CreatedDate)
    SELECT 0, GETDATE(), SolutionLogTypeID, LogTypeCode, LogTypeName, SortOrder, CreatedDate FROM Deleted; END;

GO
