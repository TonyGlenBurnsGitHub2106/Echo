SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.trg_SolutionTopicType_Insert') IS NOT NULL DROP TRIGGER dbo.trg_SolutionTopicType_Insert
GO
-- Story 5695 (Echo): keeps dbo.SolutionTopicType_LOG -- every insert lands there.
CREATE TRIGGER dbo.trg_SolutionTopicType_Insert ON dbo.SolutionTopicType AFTER INSERT AS
BEGIN SET NOCOUNT ON;
    INSERT INTO dbo.SolutionTopicType_LOG (IsInsertion, SolutionTopicTypeID, SolutionID, TypeName, NormalizedName, FigureLabel, SortOrder, CreatedDate, ModifiedDate)
    SELECT 1, SolutionTopicTypeID, SolutionID, TypeName, NormalizedName, FigureLabel, SortOrder, CreatedDate, ModifiedDate FROM Inserted; END;

GO
