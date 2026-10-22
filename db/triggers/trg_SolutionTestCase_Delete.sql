SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.trg_SolutionTestCase_Delete') IS NOT NULL DROP TRIGGER dbo.trg_SolutionTestCase_Delete
GO
/*

Trigger trg_SolutionTestCase_Delete on dbo.SolutionTestCase.

VERSION HISTORY

Date        Developer    Description
----------  -----------  ----------------------------------------------------------
2026-09-21  Felix        Solution testing checklists: audit row into SolutionTestCase_LOG.
2026-09-21  Felix        Carries IncludeInPrint.

*/

CREATE TRIGGER dbo.trg_SolutionTestCase_Delete ON dbo.SolutionTestCase AFTER DELETE AS
BEGIN SET NOCOUNT ON;
    INSERT INTO dbo.SolutionTestCase_LOG (IsInsertion, DeletedDate, SolutionTestCaseID, SolutionTestGroupID, CaseText, CaseNote, SortOrder, IncludeInPrint, CreatedDate, ModifiedDate)
    SELECT 0, GETDATE(), SolutionTestCaseID, SolutionTestGroupID, CaseText, CaseNote, SortOrder, IncludeInPrint, CreatedDate, ModifiedDate FROM Deleted; END;

GO
