SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.trg_SolutionLink_Update') IS NOT NULL DROP TRIGGER dbo.trg_SolutionLink_Update
GO
/*

Trigger trg_SolutionLink_Update on dbo.SolutionLink.

VERSION HISTORY

Date        Developer    Description
----------  -----------  ----------------------------------------------------------
2026-11-02  Felix        Solution links: audit row into SolutionLink_LOG.

*/

CREATE TRIGGER dbo.trg_SolutionLink_Update ON dbo.SolutionLink AFTER UPDATE AS
BEGIN SET NOCOUNT ON;
    INSERT INTO dbo.SolutionLink_LOG (IsInsertion, SolutionLinkID, SolutionID, FromKind, FromID, ToKind, ToID, CreatedByMemberID, CreatedDate)
    SELECT 0, SolutionLinkID, SolutionID, FromKind, FromID, ToKind, ToID, CreatedByMemberID, CreatedDate FROM Inserted; END;

GO
