SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.trg_SolutionQuestion_Insert') IS NOT NULL DROP TRIGGER dbo.trg_SolutionQuestion_Insert
GO
-- Story 5695 (Echo): keeps dbo.SolutionQuestion_LOG -- every insert lands there.
CREATE TRIGGER dbo.trg_SolutionQuestion_Insert ON dbo.SolutionQuestion AFTER INSERT AS
BEGIN SET NOCOUNT ON;
    INSERT INTO dbo.SolutionQuestion_LOG (IsInsertion, SolutionQuestionID, SolutionID, QuestionText, QuestionDetail, StatusCode, AskedByMemberID, OwnerMemberID, AnsweredBySolutionDecisionID, AnsweredDate, SolutionLogID, ModifiedBy, CreatedDate, ModifiedDate)
    SELECT 1, SolutionQuestionID, SolutionID, QuestionText, QuestionDetail, StatusCode, AskedByMemberID, OwnerMemberID, AnsweredBySolutionDecisionID, AnsweredDate, SolutionLogID, ModifiedBy, CreatedDate, ModifiedDate FROM Inserted; END;

GO
