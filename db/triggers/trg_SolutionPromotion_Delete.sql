SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.trg_SolutionPromotion_Delete') IS NOT NULL DROP TRIGGER dbo.trg_SolutionPromotion_Delete
GO
/*

Trigger trg_SolutionPromotion_Delete on dbo.SolutionPromotion.

VERSION HISTORY

Date        Developer    Description
----------  -----------  ----------------------------------------------------------
2026-11-04  Felix        Story 5695 (Echo) Release 5: audit row into SolutionPromotion_LOG.

*/

CREATE TRIGGER dbo.trg_SolutionPromotion_Delete ON dbo.SolutionPromotion AFTER DELETE AS
BEGIN SET NOCOUNT ON;
    INSERT INTO dbo.SolutionPromotion_LOG (IsInsertion, DeletedDate, SolutionPromotionID, SolutionID, SourceKind, SourceID, TargetKind, TargetID, PromotedByMemberID, CreatedDate)
    SELECT 0, GETDATE(), SolutionPromotionID, SolutionID, SourceKind, SourceID, TargetKind, TargetID, PromotedByMemberID, CreatedDate FROM Deleted; END;

GO
