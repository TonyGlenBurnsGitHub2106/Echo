SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.trg_SolutionTopic_Delete') IS NOT NULL DROP TRIGGER dbo.trg_SolutionTopic_Delete
GO
-- Story 5695 (Echo): keeps dbo.SolutionTopic_LOG -- every delete lands there.
CREATE TRIGGER dbo.trg_SolutionTopic_Delete ON dbo.SolutionTopic AFTER DELETE AS
BEGIN SET NOCOUNT ON;
    INSERT INTO dbo.SolutionTopic_LOG (IsInsertion, DeletedDate, SolutionTopicID, SolutionID, TopicName, NormalizedName, TopicSummary, AuthorMemberID, ModifiedBy, CreatedDate, ModifiedDate, TopicKindCode, SolutionTopicTypeID, SolutionStatusID, FigureValue, AssumptionHolds, SortOrder)
    SELECT 0, GETDATE(), SolutionTopicID, SolutionID, TopicName, NormalizedName, TopicSummary, AuthorMemberID, ModifiedBy, CreatedDate, ModifiedDate, TopicKindCode, SolutionTopicTypeID, SolutionStatusID, FigureValue, AssumptionHolds, SortOrder FROM Deleted; END;

GO
