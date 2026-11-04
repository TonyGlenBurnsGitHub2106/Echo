SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.trg_SolutionTopic_Update') IS NOT NULL DROP TRIGGER dbo.trg_SolutionTopic_Update
GO
/*

Trigger trg_SolutionTopic_Update on dbo.SolutionTopic.

VERSION HISTORY

Date        Developer    Description
----------  -----------  ----------------------------------------------------------
2026-11-02  Felix        Solution topics: audit row into SolutionTopic_LOG.
2026-11-04  Felix        Story 5695 (Echo) Release 3: the kind columns (TopicKindCode ... SortOrder).

*/

CREATE TRIGGER dbo.trg_SolutionTopic_Update ON dbo.SolutionTopic AFTER UPDATE AS
BEGIN SET NOCOUNT ON;
    INSERT INTO dbo.SolutionTopic_LOG (IsInsertion, SolutionTopicID, SolutionID, TopicName, NormalizedName, TopicSummary, AuthorMemberID, ModifiedBy, CreatedDate, ModifiedDate, TopicKindCode, SolutionTopicTypeID, SolutionStatusID, FigureValue, AssumptionHolds, SortOrder)
    SELECT 0, SolutionTopicID, SolutionID, TopicName, NormalizedName, TopicSummary, AuthorMemberID, ModifiedBy, CreatedDate, ModifiedDate, TopicKindCode, SolutionTopicTypeID, SolutionStatusID, FigureValue, AssumptionHolds, SortOrder FROM Inserted; END;

GO
