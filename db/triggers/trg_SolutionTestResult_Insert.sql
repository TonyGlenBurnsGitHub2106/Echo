SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.trg_SolutionTestResult_Insert') IS NOT NULL DROP TRIGGER dbo.trg_SolutionTestResult_Insert
GO
/*

Trigger trg_SolutionTestResult_Insert on dbo.SolutionTestResult.

VERSION HISTORY

Date        Developer    Description
----------  -----------  ----------------------------------------------------------
2026-09-25  Felix        Solution testing checklists: audit row into SolutionTestResult_LOG.

*/

CREATE TRIGGER dbo.trg_SolutionTestResult_Insert ON dbo.SolutionTestResult AFTER INSERT AS
BEGIN SET NOCOUNT ON;
    INSERT INTO dbo.SolutionTestResult_LOG (IsInsertion, SolutionTestResultID, SolutionTestCaseID, IsFailing, FailingDate, ResultNote, ModifiedByMemberID, CreatedDate, ModifiedDate)
    SELECT 1, SolutionTestResultID, SolutionTestCaseID, IsFailing, FailingDate, ResultNote, ModifiedByMemberID, CreatedDate, ModifiedDate FROM Inserted; END;

GO
