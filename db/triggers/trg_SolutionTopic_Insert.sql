SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.trg_SolutionTopic_Insert') IS NOT NULL DROP TRIGGER dbo.trg_SolutionTopic_Insert
GO
-- Story 5695 (Echo): keeps dbo.SolutionTopic_LOG -- every insert lands there.
CREATE TRIGGER dbo.trg_SolutionTopic_Insert ON dbo.SolutionTopic AFTER INSERT AS
BEGIN SET NOCOUNT ON;
    INSERT INTO dbo.SolutionTopic_LOG (IsInsertion, SolutionTopicID, SolutionID, TopicName, NormalizedName, TopicSummary, AuthorMemberID, ModifiedBy, CreatedDate, ModifiedDate, TopicKindCode, SolutionTopicTypeID, SolutionStatusID, FigureValue, AssumptionHolds, SortOrder)
    SELECT 1, SolutionTopicID, SolutionID, TopicName, NormalizedName, TopicSummary, AuthorMemberID, ModifiedBy, CreatedDate, ModifiedDate, TopicKindCode, SolutionTopicTypeID, SolutionStatusID, FigureValue, AssumptionHolds, SortOrder FROM Inserted; END;

GO
