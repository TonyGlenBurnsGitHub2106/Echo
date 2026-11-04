SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.trg_SolutionDecision_Delete') IS NOT NULL DROP TRIGGER dbo.trg_SolutionDecision_Delete
GO
-- Story 5695 (Echo): keeps dbo.SolutionDecision_LOG -- every delete lands there.
CREATE TRIGGER dbo.trg_SolutionDecision_Delete ON dbo.SolutionDecision AFTER DELETE AS
BEGIN SET NOCOUNT ON;
    INSERT INTO dbo.SolutionDecision_LOG (IsInsertion, DeletedDate, SolutionDecisionID, SolutionID, DecisionTitle, Context, OptionsText, Outcome, StatusCode, SupersededBySolutionDecisionID, DecidedDate, SolutionLogID, DecidedByMemberID, ModifiedBy, CreatedDate, ModifiedDate)
    SELECT 0, GETDATE(), SolutionDecisionID, SolutionID, DecisionTitle, Context, OptionsText, Outcome, StatusCode, SupersededBySolutionDecisionID, DecidedDate, SolutionLogID, DecidedByMemberID, ModifiedBy, CreatedDate, ModifiedDate FROM Deleted; END;

GO
