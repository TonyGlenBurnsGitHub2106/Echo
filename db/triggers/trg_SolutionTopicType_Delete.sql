SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.trg_SolutionTopicType_Delete') IS NOT NULL DROP TRIGGER dbo.trg_SolutionTopicType_Delete
GO
-- Story 5695 (Echo): keeps dbo.SolutionTopicType_LOG -- every delete lands there.
CREATE TRIGGER dbo.trg_SolutionTopicType_Delete ON dbo.SolutionTopicType AFTER DELETE AS
BEGIN SET NOCOUNT ON;
    INSERT INTO dbo.SolutionTopicType_LOG (IsInsertion, DeletedDate, SolutionTopicTypeID, SolutionID, TypeName, NormalizedName, FigureLabel, SortOrder, CreatedDate, ModifiedDate)
    SELECT 0, GETDATE(), SolutionTopicTypeID, SolutionID, TypeName, NormalizedName, FigureLabel, SortOrder, CreatedDate, ModifiedDate FROM Deleted; END;

GO
