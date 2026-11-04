SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.trg_SolutionQuestion_Update') IS NOT NULL DROP TRIGGER dbo.trg_SolutionQuestion_Update
GO
-- Story 5695 (Echo): keeps dbo.SolutionQuestion_LOG -- every update lands there.
CREATE TRIGGER dbo.trg_SolutionQuestion_Update ON dbo.SolutionQuestion AFTER UPDATE AS
BEGIN SET NOCOUNT ON;
    INSERT INTO dbo.SolutionQuestion_LOG (IsInsertion, SolutionQuestionID, SolutionID, QuestionText, QuestionDetail, StatusCode, AskedByMemberID, OwnerMemberID, AnsweredBySolutionDecisionID, AnsweredDate, SolutionLogID, ModifiedBy, CreatedDate, ModifiedDate)
    SELECT 0, SolutionQuestionID, SolutionID, QuestionText, QuestionDetail, StatusCode, AskedByMemberID, OwnerMemberID, AnsweredBySolutionDecisionID, AnsweredDate, SolutionLogID, ModifiedBy, CreatedDate, ModifiedDate FROM Inserted; END;

GO
