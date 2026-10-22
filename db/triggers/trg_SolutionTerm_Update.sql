SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.trg_SolutionTerm_Update') IS NOT NULL DROP TRIGGER dbo.trg_SolutionTerm_Update
GO
/*

Trigger trg_SolutionTerm_Update on dbo.SolutionTerm.

VERSION HISTORY

Date        Developer    Description
----------  -----------  ----------------------------------------------------------
2026-10-09  Felix        Solution Terms tab: audit row into SolutionTerm_LOG.

*/

CREATE TRIGGER dbo.trg_SolutionTerm_Update ON dbo.SolutionTerm AFTER UPDATE AS
BEGIN SET NOCOUNT ON;
    INSERT INTO dbo.SolutionTerm_LOG (IsInsertion, SolutionTermID, SolutionID, Term, NormalizedTerm, Definition, SolutionTermTypeID, TermSource, TermNotes, IsFavorite, AuthorMemberID, ModifiedByMemberID, CreatedDate, ModifiedDate)
    SELECT 0, SolutionTermID, SolutionID, Term, NormalizedTerm, Definition, SolutionTermTypeID, TermSource, TermNotes, IsFavorite, AuthorMemberID, ModifiedByMemberID, CreatedDate, ModifiedDate FROM Inserted; END;

GO
