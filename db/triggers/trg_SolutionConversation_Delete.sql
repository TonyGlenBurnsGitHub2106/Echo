SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.trg_SolutionConversation_Delete') IS NOT NULL DROP TRIGGER dbo.trg_SolutionConversation_Delete
GO
/*

Trigger trg_SolutionConversation_Delete on dbo.SolutionConversation.

VERSION HISTORY

Date        Developer    Description
----------  -----------  ----------------------------------------------------------
2026-09-17  Felix        Story 3493: audit row into SolutionConversation_LOG (the Solutions family).

*/

CREATE TRIGGER dbo.trg_SolutionConversation_Delete ON dbo.SolutionConversation AFTER DELETE AS
BEGIN SET NOCOUNT ON;
    INSERT INTO dbo.SolutionConversation_LOG (IsInsertion, DeletedDate, SolutionConversationID, SolutionID, ConversationID, CreatedDate)
    SELECT 0, GETDATE(), SolutionConversationID, SolutionID, ConversationID, CreatedDate FROM Deleted; END;

GO
