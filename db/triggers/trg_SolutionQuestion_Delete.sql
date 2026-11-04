SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.trg_SolutionQuestion_Delete') IS NOT NULL DROP TRIGGER dbo.trg_SolutionQuestion_Delete
GO
-- Story 5695 (Echo): keeps dbo.SolutionQuestion_LOG -- every delete lands there.
CREATE TRIGGER dbo.trg_SolutionQuestion_Delete ON dbo.SolutionQuestion AFTER DELETE AS
BEGIN SET NOCOUNT ON;
    INSERT INTO dbo.SolutionQuestion_LOG (IsInsertion, DeletedDate, SolutionQuestionID, SolutionID, QuestionText, QuestionDetail, StatusCode, AskedByMemberID, OwnerMemberID, AnsweredBySolutionDecisionID, AnsweredDate, SolutionLogID, ModifiedBy, CreatedDate, ModifiedDate)
    SELECT 0, GETDATE(), SolutionQuestionID, SolutionID, QuestionText, QuestionDetail, StatusCode, AskedByMemberID, OwnerMemberID, AnsweredBySolutionDecisionID, AnsweredDate, SolutionLogID, ModifiedBy, CreatedDate, ModifiedDate FROM Deleted; END;

GO
