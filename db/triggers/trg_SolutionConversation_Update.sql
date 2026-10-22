SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.trg_SolutionConversation_Update') IS NOT NULL DROP TRIGGER dbo.trg_SolutionConversation_Update
GO
/*

Trigger trg_SolutionConversation_Update on dbo.SolutionConversation.

VERSION HISTORY

Date        Developer    Description
----------  -----------  ----------------------------------------------------------
2026-09-17  Felix        Story 3493: audit row into SolutionConversation_LOG (the Solutions family).

*/

CREATE TRIGGER dbo.trg_SolutionConversation_Update ON dbo.SolutionConversation AFTER UPDATE AS
BEGIN SET NOCOUNT ON;
    INSERT INTO dbo.SolutionConversation_LOG (IsInsertion, SolutionConversationID, SolutionID, ConversationID, CreatedDate)
    SELECT 0, SolutionConversationID, SolutionID, ConversationID, CreatedDate FROM Inserted; END;

GO
