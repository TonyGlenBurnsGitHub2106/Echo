SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.trg_SolutionLogType_Insert') IS NOT NULL DROP TRIGGER dbo.trg_SolutionLogType_Insert
GO
/*

Trigger trg_SolutionLogType_Insert on dbo.SolutionLogType.

VERSION HISTORY

Date        Developer    Description
----------  -----------  ----------------------------------------------------------
2026-09-17  Felix        Story 3493: audit row into SolutionLogType_LOG (the Solutions family).

*/

CREATE TRIGGER dbo.trg_SolutionLogType_Insert ON dbo.SolutionLogType AFTER INSERT AS
BEGIN SET NOCOUNT ON;
    INSERT INTO dbo.SolutionLogType_LOG (IsInsertion, SolutionLogTypeID, LogTypeCode, LogTypeName, SortOrder, CreatedDate)
    SELECT 1, SolutionLogTypeID, LogTypeCode, LogTypeName, SortOrder, CreatedDate FROM Inserted; END;

GO
