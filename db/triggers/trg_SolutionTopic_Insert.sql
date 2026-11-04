SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.trg_SolutionTopic_Insert') IS NOT NULL DROP TRIGGER dbo.trg_SolutionTopic_Insert
GO
/*

Trigger trg_SolutionTopic_Insert on dbo.SolutionTopic.

VERSION HISTORY

Date        Developer    Description
----------  -----------  ----------------------------------------------------------
2026-11-02  Felix        Solution topics: audit row into SolutionTopic_LOG.

*/

CREATE TRIGGER dbo.trg_SolutionTopic_Insert ON dbo.SolutionTopic AFTER INSERT AS
BEGIN SET NOCOUNT ON;
    INSERT INTO dbo.SolutionTopic_LOG (IsInsertion, SolutionTopicID, SolutionID, TopicName, NormalizedName, TopicSummary, AuthorMemberID, ModifiedBy, CreatedDate, ModifiedDate)
    SELECT 1, SolutionTopicID, SolutionID, TopicName, NormalizedName, TopicSummary, AuthorMemberID, ModifiedBy, CreatedDate, ModifiedDate FROM Inserted; END;

GO
