SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.trg_SolutionSavedView_Insert') IS NOT NULL DROP TRIGGER dbo.trg_SolutionSavedView_Insert
GO
/*

Trigger trg_SolutionSavedView_Insert on dbo.SolutionSavedView.

VERSION HISTORY

Date        Developer    Description
----------  -----------  ----------------------------------------------------------
2026-11-04  Felix        Story 5695 (Echo) Release 5: audit row into SolutionSavedView_LOG.

*/

CREATE TRIGGER dbo.trg_SolutionSavedView_Insert ON dbo.SolutionSavedView AFTER INSERT AS
BEGIN SET NOCOUNT ON;
    INSERT INTO dbo.SolutionSavedView_LOG (IsInsertion, SolutionSavedViewID, SolutionID, SolutionMemberID, ScopeCode, ViewName, NormalizedName, FilterJson, SortOrder, CreatedDate, ModifiedDate)
    SELECT 1, SolutionSavedViewID, SolutionID, SolutionMemberID, ScopeCode, ViewName, NormalizedName, FilterJson, SortOrder, CreatedDate, ModifiedDate FROM Inserted; END;

GO
