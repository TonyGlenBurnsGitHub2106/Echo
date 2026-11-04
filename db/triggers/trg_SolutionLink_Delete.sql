SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.trg_SolutionLink_Delete') IS NOT NULL DROP TRIGGER dbo.trg_SolutionLink_Delete
GO
/*

Trigger trg_SolutionLink_Delete on dbo.SolutionLink.

VERSION HISTORY

Date        Developer    Description
----------  -----------  ----------------------------------------------------------
2026-11-02  Felix        Solution links: audit row into SolutionLink_LOG.

*/

CREATE TRIGGER dbo.trg_SolutionLink_Delete ON dbo.SolutionLink AFTER DELETE AS
BEGIN SET NOCOUNT ON;
    INSERT INTO dbo.SolutionLink_LOG (IsInsertion, DeletedDate, SolutionLinkID, SolutionID, FromKind, FromID, ToKind, ToID, CreatedByMemberID, CreatedDate)
    SELECT 0, GETDATE(), SolutionLinkID, SolutionID, FromKind, FromID, ToKind, ToID, CreatedByMemberID, CreatedDate FROM Deleted; END;

GO
