SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.trg_SolutionTestCase_Insert') IS NOT NULL DROP TRIGGER dbo.trg_SolutionTestCase_Insert
GO
/*

Trigger trg_SolutionTestCase_Insert on dbo.SolutionTestCase.

VERSION HISTORY

Date        Developer    Description
----------  -----------  ----------------------------------------------------------
2026-09-21  Felix        Solution testing checklists: audit row into SolutionTestCase_LOG.
2026-09-21  Felix        Carries IncludeInPrint.

*/

CREATE TRIGGER dbo.trg_SolutionTestCase_Insert ON dbo.SolutionTestCase AFTER INSERT AS
BEGIN SET NOCOUNT ON;
    INSERT INTO dbo.SolutionTestCase_LOG (IsInsertion, SolutionTestCaseID, SolutionTestGroupID, CaseText, CaseNote, SortOrder, IncludeInPrint, CreatedDate, ModifiedDate)
    SELECT 1, SolutionTestCaseID, SolutionTestGroupID, CaseText, CaseNote, SortOrder, IncludeInPrint, CreatedDate, ModifiedDate FROM Inserted; END;

GO
