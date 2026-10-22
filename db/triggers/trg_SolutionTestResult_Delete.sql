SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.trg_SolutionTestResult_Delete') IS NOT NULL DROP TRIGGER dbo.trg_SolutionTestResult_Delete
GO
/*

Trigger trg_SolutionTestResult_Delete on dbo.SolutionTestResult.

VERSION HISTORY

Date        Developer    Description
----------  -----------  ----------------------------------------------------------
2026-09-25  Felix        Solution testing checklists: audit row into SolutionTestResult_LOG.

*/

CREATE TRIGGER dbo.trg_SolutionTestResult_Delete ON dbo.SolutionTestResult AFTER DELETE AS
BEGIN SET NOCOUNT ON;
    INSERT INTO dbo.SolutionTestResult_LOG (IsInsertion, DeletedDate, SolutionTestResultID, SolutionTestCaseID, IsFailing, FailingDate, ResultNote, ModifiedByMemberID, CreatedDate, ModifiedDate)
    SELECT 0, GETDATE(), SolutionTestResultID, SolutionTestCaseID, IsFailing, FailingDate, ResultNote, ModifiedByMemberID, CreatedDate, ModifiedDate FROM Deleted; END;

GO
