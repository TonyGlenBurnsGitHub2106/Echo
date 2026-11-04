SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.trg_SolutionSavedView_Delete') IS NOT NULL DROP TRIGGER dbo.trg_SolutionSavedView_Delete
GO
/*

Trigger trg_SolutionSavedView_Delete on dbo.SolutionSavedView.

VERSION HISTORY

Date        Developer    Description
----------  -----------  ----------------------------------------------------------
2026-11-04  Felix        Story 5695 (Echo) Release 5: audit row into SolutionSavedView_LOG.

*/

CREATE TRIGGER dbo.trg_SolutionSavedView_Delete ON dbo.SolutionSavedView AFTER DELETE AS
BEGIN SET NOCOUNT ON;
    INSERT INTO dbo.SolutionSavedView_LOG (IsInsertion, DeletedDate, SolutionSavedViewID, SolutionID, SolutionMemberID, ScopeCode, ViewName, NormalizedName, FilterJson, SortOrder, CreatedDate, ModifiedDate)
    SELECT 0, GETDATE(), SolutionSavedViewID, SolutionID, SolutionMemberID, ScopeCode, ViewName, NormalizedName, FilterJson, SortOrder, CreatedDate, ModifiedDate FROM Deleted; END;

GO
