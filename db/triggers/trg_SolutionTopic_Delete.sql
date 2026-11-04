SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.trg_SolutionTopic_Delete') IS NOT NULL DROP TRIGGER dbo.trg_SolutionTopic_Delete
GO
/*

Trigger trg_SolutionTopic_Delete on dbo.SolutionTopic.

VERSION HISTORY

Date        Developer    Description
----------  -----------  ----------------------------------------------------------
2026-11-02  Felix        Solution topics: audit row into SolutionTopic_LOG.

*/

CREATE TRIGGER dbo.trg_SolutionTopic_Delete ON dbo.SolutionTopic AFTER DELETE AS
BEGIN SET NOCOUNT ON;
    INSERT INTO dbo.SolutionTopic_LOG (IsInsertion, DeletedDate, SolutionTopicID, SolutionID, TopicName, NormalizedName, TopicSummary, AuthorMemberID, ModifiedBy, CreatedDate, ModifiedDate)
    SELECT 0, GETDATE(), SolutionTopicID, SolutionID, TopicName, NormalizedName, TopicSummary, AuthorMemberID, ModifiedBy, CreatedDate, ModifiedDate FROM Deleted; END;

GO
