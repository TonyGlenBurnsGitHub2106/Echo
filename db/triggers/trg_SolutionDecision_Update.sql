SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.trg_SolutionDecision_Update') IS NOT NULL DROP TRIGGER dbo.trg_SolutionDecision_Update
GO
-- Story 5695 (Echo): keeps dbo.SolutionDecision_LOG -- every update lands there.
CREATE TRIGGER dbo.trg_SolutionDecision_Update ON dbo.SolutionDecision AFTER UPDATE AS
BEGIN SET NOCOUNT ON;
    INSERT INTO dbo.SolutionDecision_LOG (IsInsertion, SolutionDecisionID, SolutionID, DecisionTitle, Context, OptionsText, Outcome, StatusCode, SupersededBySolutionDecisionID, DecidedDate, SolutionLogID, DecidedByMemberID, ModifiedBy, CreatedDate, ModifiedDate)
    SELECT 0, SolutionDecisionID, SolutionID, DecisionTitle, Context, OptionsText, Outcome, StatusCode, SupersededBySolutionDecisionID, DecidedDate, SolutionLogID, DecidedByMemberID, ModifiedBy, CreatedDate, ModifiedDate FROM Inserted; END;

GO
