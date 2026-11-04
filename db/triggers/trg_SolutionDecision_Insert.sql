SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.trg_SolutionDecision_Insert') IS NOT NULL DROP TRIGGER dbo.trg_SolutionDecision_Insert
GO
-- Story 5695 (Echo): keeps dbo.SolutionDecision_LOG -- every insert lands there.
CREATE TRIGGER dbo.trg_SolutionDecision_Insert ON dbo.SolutionDecision AFTER INSERT AS
BEGIN SET NOCOUNT ON;
    INSERT INTO dbo.SolutionDecision_LOG (IsInsertion, SolutionDecisionID, SolutionID, DecisionTitle, Context, OptionsText, Outcome, StatusCode, SupersededBySolutionDecisionID, DecidedDate, SolutionLogID, DecidedByMemberID, ModifiedBy, CreatedDate, ModifiedDate)
    SELECT 1, SolutionDecisionID, SolutionID, DecisionTitle, Context, OptionsText, Outcome, StatusCode, SupersededBySolutionDecisionID, DecidedDate, SolutionLogID, DecidedByMemberID, ModifiedBy, CreatedDate, ModifiedDate FROM Inserted; END;

GO
