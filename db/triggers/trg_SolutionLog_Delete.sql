SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.trg_SolutionLog_Delete') IS NOT NULL DROP TRIGGER dbo.trg_SolutionLog_Delete
GO
/*

Trigger trg_SolutionLog_Delete on dbo.SolutionLog.

VERSION HISTORY

Date        Developer    Description
----------  -----------  ----------------------------------------------------------
2026-09-17  Felix        Story 3493: audit row into SolutionLog_LOG (the Solutions family).

*/

CREATE TRIGGER dbo.trg_SolutionLog_Delete ON dbo.SolutionLog AFTER DELETE AS
BEGIN SET NOCOUNT ON;
    INSERT INTO dbo.SolutionLog_LOG (IsInsertion, DeletedDate, SolutionLogID, SolutionID, SolutionProjectID, SolutionProjectItemID, SequenceNumber, SolutionLogTypeID, AuthorMemberID, LogText, LogDate, CreatedDate, ModifiedDate)
    SELECT 0, GETDATE(), SolutionLogID, SolutionID, SolutionProjectID, SolutionProjectItemID, SequenceNumber, SolutionLogTypeID, AuthorMemberID, LogText, LogDate, CreatedDate, ModifiedDate FROM Deleted; END;

GO
