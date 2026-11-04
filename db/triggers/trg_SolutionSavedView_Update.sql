SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.trg_SolutionSavedView_Update') IS NOT NULL DROP TRIGGER dbo.trg_SolutionSavedView_Update
GO
/*

Trigger trg_SolutionSavedView_Update on dbo.SolutionSavedView.

VERSION HISTORY

Date        Developer    Description
----------  -----------  ----------------------------------------------------------
2026-11-04  Felix        Story 5695 (Echo) Release 5: audit row into SolutionSavedView_LOG.

*/

CREATE TRIGGER dbo.trg_SolutionSavedView_Update ON dbo.SolutionSavedView AFTER UPDATE AS
BEGIN SET NOCOUNT ON;
    INSERT INTO dbo.SolutionSavedView_LOG (IsInsertion, SolutionSavedViewID, SolutionID, SolutionMemberID, ScopeCode, ViewName, NormalizedName, FilterJson, SortOrder, CreatedDate, ModifiedDate)
    SELECT 0, SolutionSavedViewID, SolutionID, SolutionMemberID, ScopeCode, ViewName, NormalizedName, FilterJson, SortOrder, CreatedDate, ModifiedDate FROM Inserted; END;

GO
