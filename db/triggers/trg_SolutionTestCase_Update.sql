SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.trg_SolutionTestCase_Update') IS NOT NULL DROP TRIGGER dbo.trg_SolutionTestCase_Update
GO
/*

Trigger trg_SolutionTestCase_Update on dbo.SolutionTestCase.

VERSION HISTORY

Date        Developer    Description
----------  -----------  ----------------------------------------------------------
2026-09-21  Felix        Solution testing checklists: audit row into SolutionTestCase_LOG.
2026-09-21  Felix        Carries IncludeInPrint.

*/

CREATE TRIGGER dbo.trg_SolutionTestCase_Update ON dbo.SolutionTestCase AFTER UPDATE AS
BEGIN SET NOCOUNT ON;
    INSERT INTO dbo.SolutionTestCase_LOG (IsInsertion, SolutionTestCaseID, SolutionTestGroupID, CaseText, CaseNote, SortOrder, IncludeInPrint, CreatedDate, ModifiedDate)
    SELECT 0, SolutionTestCaseID, SolutionTestGroupID, CaseText, CaseNote, SortOrder, IncludeInPrint, CreatedDate, ModifiedDate FROM Inserted; END;

GO
