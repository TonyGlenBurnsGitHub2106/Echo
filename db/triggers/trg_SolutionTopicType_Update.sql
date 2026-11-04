SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.trg_SolutionTopicType_Update') IS NOT NULL DROP TRIGGER dbo.trg_SolutionTopicType_Update
GO
-- Story 5695 (Echo): keeps dbo.SolutionTopicType_LOG -- every update lands there.
CREATE TRIGGER dbo.trg_SolutionTopicType_Update ON dbo.SolutionTopicType AFTER UPDATE AS
BEGIN SET NOCOUNT ON;
    INSERT INTO dbo.SolutionTopicType_LOG (IsInsertion, SolutionTopicTypeID, SolutionID, TypeName, NormalizedName, FigureLabel, SortOrder, CreatedDate, ModifiedDate)
    SELECT 0, SolutionTopicTypeID, SolutionID, TypeName, NormalizedName, FigureLabel, SortOrder, CreatedDate, ModifiedDate FROM Inserted; END;

GO
