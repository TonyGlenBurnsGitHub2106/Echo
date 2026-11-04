SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.trg_SolutionPromotion_Insert') IS NOT NULL DROP TRIGGER dbo.trg_SolutionPromotion_Insert
GO
/*

Trigger trg_SolutionPromotion_Insert on dbo.SolutionPromotion.

VERSION HISTORY

Date        Developer    Description
----------  -----------  ----------------------------------------------------------
2026-11-04  Felix        Story 5695 (Echo) Release 5: audit row into SolutionPromotion_LOG.

*/

CREATE TRIGGER dbo.trg_SolutionPromotion_Insert ON dbo.SolutionPromotion AFTER INSERT AS
BEGIN SET NOCOUNT ON;
    INSERT INTO dbo.SolutionPromotion_LOG (IsInsertion, SolutionPromotionID, SolutionID, SourceKind, SourceID, TargetKind, TargetID, PromotedByMemberID, CreatedDate)
    SELECT 1, SolutionPromotionID, SolutionID, SourceKind, SourceID, TargetKind, TargetID, PromotedByMemberID, CreatedDate FROM Inserted; END;

GO
