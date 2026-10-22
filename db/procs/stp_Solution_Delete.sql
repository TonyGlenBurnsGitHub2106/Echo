SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_Solution_Delete') IS NOT NULL DROP PROCEDURE dbo.stp_Solution_Delete
GO
CREATE PROCEDURE dbo.stp_Solution_Delete
    @prmSolutionID INT
AS

/*

TEST
----

    EXEC stp_Solution_Delete 0

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------------------------------------
09/17/2026  Felix        Story 3493: remove one solution and everything that belongs to it -- note steps and versions,
                         notes, the journal, items, projects, client visibility, and the LINKS to its
                         conversations (the conversations themselves are left alone). One transaction. Every
                         row removed lands in its _LOG twin through the delete triggers. Returns RowsAffected.
09/17/2026  Felix        Story 3503: also detach the solution from every record it was attached to
                         (EntitySolutionXRef). The records themselves are untouched.
09/21/2026  Felix        Testing checklists: also remove the solution's testing checklists -- ticks, cases, groups,
                         columns and the checklists themselves. The level titles stay in their lookup.
09/21/2026  Felix        Story 3516: also remove the solution's configuration -- values and their also-set rows,
                         properties, groups and environments. The name / source lookups stay.

09/22/2026  Felix        Solutions documents are now NOTES (SolutionDocument* -> SolutionNote*, Tony 09/22/2026).
09/25/2026  Felix        Also removes the test cases' results (failing marks and result notes, SolutionTestResult).
09/25/2026  Felix        Story 3534: also removes the testing checklists' skipped cells (SolutionTestSkip).
10/01/2026  Felix        Story 5566: a solution is now a true entity. Also removes anything that hangs off its OWN
                         EntityID -- notes (tblEntityEventXref), Leonardo Files links (EntityLeonardoFileXRef) and any
                         EntitySolutionXRef row naming it -- the set stp_CallLog_Delete / stp_Vehicle_Delete clear.
10/05/2026  Felix        Story 5594: also removes the files' filing (category, Important, date -- SolutionFileFiling).
10/09/2026  Felix        Terms tab: also removes the solution's glossary -- terms, their category links, and the
                         solution's own term types and categories.
10/09/2026  Felix        Story 5611: also removes Configuration II -- cells and their other places, properties, groups,
                         column tags, columns, axis values and axes.

*/

SET NOCOUNT ON

DECLARE @rows INT = 0
DECLARE @entityId INT = (SELECT EntityID FROM dbo.Solution WHERE SolutionID = @prmSolutionID)

BEGIN TRY
    BEGIN TRANSACTION

    DELETE st FROM dbo.SolutionNoteStep st
    JOIN   dbo.SolutionNote d ON d.SolutionNoteID = st.SolutionNoteID WHERE d.SolutionID = @prmSolutionID
    DELETE v FROM dbo.SolutionNoteVersion v
    JOIN   dbo.SolutionNote d ON d.SolutionNoteID = v.SolutionNoteID WHERE d.SolutionID = @prmSolutionID
    DELETE k FROM dbo.SolutionTestCheck k
    JOIN   dbo.SolutionTestPlanLevel pl ON pl.SolutionTestPlanLevelID = k.SolutionTestPlanLevelID
    JOIN   dbo.SolutionTestPlan tp ON tp.SolutionTestPlanID = pl.SolutionTestPlanID WHERE tp.SolutionID = @prmSolutionID
    DELETE s FROM dbo.SolutionTestSkip s
    JOIN   dbo.SolutionTestPlanLevel pl ON pl.SolutionTestPlanLevelID = s.SolutionTestPlanLevelID
    JOIN   dbo.SolutionTestPlan tp ON tp.SolutionTestPlanID = pl.SolutionTestPlanID WHERE tp.SolutionID = @prmSolutionID
    DELETE r FROM dbo.SolutionTestResult r
    JOIN   dbo.SolutionTestCase c  ON c.SolutionTestCaseID  = r.SolutionTestCaseID
    JOIN   dbo.SolutionTestGroup g ON g.SolutionTestGroupID = c.SolutionTestGroupID
    JOIN   dbo.SolutionTestPlan tp ON tp.SolutionTestPlanID = g.SolutionTestPlanID WHERE tp.SolutionID = @prmSolutionID
    DELETE c FROM dbo.SolutionTestCase c
    JOIN   dbo.SolutionTestGroup g ON g.SolutionTestGroupID = c.SolutionTestGroupID
    JOIN   dbo.SolutionTestPlan tp ON tp.SolutionTestPlanID = g.SolutionTestPlanID WHERE tp.SolutionID = @prmSolutionID
    DELETE g FROM dbo.SolutionTestGroup g
    JOIN   dbo.SolutionTestPlan tp ON tp.SolutionTestPlanID = g.SolutionTestPlanID WHERE tp.SolutionID = @prmSolutionID
    DELETE pl FROM dbo.SolutionTestPlanLevel pl
    JOIN   dbo.SolutionTestPlan tp ON tp.SolutionTestPlanID = pl.SolutionTestPlanID WHERE tp.SolutionID = @prmSolutionID
    DELETE FROM dbo.SolutionTestPlan WHERE SolutionID = @prmSolutionID
    DELETE a FROM dbo.SolutionConfigValueAlsoSet a
    JOIN   dbo.SolutionConfigValue cv ON cv.SolutionConfigValueID = a.SolutionConfigValueID
    JOIN   dbo.SolutionConfigEnvironment ce ON ce.SolutionConfigEnvironmentID = cv.SolutionConfigEnvironmentID WHERE ce.SolutionID = @prmSolutionID
    DELETE cv FROM dbo.SolutionConfigValue cv
    JOIN   dbo.SolutionConfigEnvironment ce ON ce.SolutionConfigEnvironmentID = cv.SolutionConfigEnvironmentID WHERE ce.SolutionID = @prmSolutionID
    DELETE p FROM dbo.SolutionConfigProperty p
    JOIN   dbo.SolutionConfigGroup g ON g.SolutionConfigGroupID = p.SolutionConfigGroupID WHERE g.SolutionID = @prmSolutionID
    DELETE FROM dbo.SolutionConfigGroup       WHERE SolutionID = @prmSolutionID
    DELETE FROM dbo.SolutionConfigEnvironment WHERE SolutionID = @prmSolutionID
    DELETE x FROM dbo.SolutionTermCategoryXRef x
    JOIN   dbo.SolutionTerm t ON t.SolutionTermID = x.SolutionTermID WHERE t.SolutionID = @prmSolutionID
    DELETE FROM dbo.SolutionTerm         WHERE SolutionID = @prmSolutionID
    DELETE FROM dbo.SolutionTermCategory WHERE SolutionID = @prmSolutionID
    DELETE FROM dbo.SolutionTermType     WHERE SolutionID = @prmSolutionID
    DELETE a FROM dbo.SolutionConfigGridValueAlsoSet a
    JOIN   dbo.SolutionConfigGridValue v    ON v.SolutionConfigGridValueID    = a.SolutionConfigGridValueID
    JOIN   dbo.SolutionConfigGridProperty p ON p.SolutionConfigGridPropertyID = v.SolutionConfigGridPropertyID WHERE p.SolutionID = @prmSolutionID
    DELETE v FROM dbo.SolutionConfigGridValue v
    JOIN   dbo.SolutionConfigGridProperty p ON p.SolutionConfigGridPropertyID = v.SolutionConfigGridPropertyID WHERE p.SolutionID = @prmSolutionID
    DELETE FROM dbo.SolutionConfigGridProperty WHERE SolutionID = @prmSolutionID
    DELETE FROM dbo.SolutionConfigGridGroup    WHERE SolutionID = @prmSolutionID
    DELETE t FROM dbo.SolutionConfigGridColumnTag t
    JOIN   dbo.SolutionConfigGridColumn c ON c.SolutionConfigGridColumnID = t.SolutionConfigGridColumnID WHERE c.SolutionID = @prmSolutionID
    DELETE FROM dbo.SolutionConfigGridColumn   WHERE SolutionID = @prmSolutionID
    DELETE av FROM dbo.SolutionConfigGridAxisValue av
    JOIN   dbo.SolutionConfigGridAxis ax ON ax.SolutionConfigGridAxisID = av.SolutionConfigGridAxisID WHERE ax.SolutionID = @prmSolutionID
    DELETE FROM dbo.SolutionConfigGridAxis     WHERE SolutionID = @prmSolutionID
    DELETE FROM dbo.SolutionNote WHERE SolutionID = @prmSolutionID
    DELETE FROM dbo.SolutionLog      WHERE SolutionID = @prmSolutionID
    DELETE fl FROM dbo.SolutionFileFiling fl
    JOIN   dbo.SolutionFile f ON f.SolutionFileID = fl.SolutionFileID WHERE f.SolutionID = @prmSolutionID
    DELETE FROM dbo.SolutionFile     WHERE SolutionID = @prmSolutionID     -- rows only; the bytes on disk are never unlinked
    DELETE i FROM dbo.SolutionProjectItem i
    JOIN   dbo.SolutionProject p ON p.SolutionProjectID = i.SolutionProjectID WHERE p.SolutionID = @prmSolutionID
    DELETE FROM dbo.SolutionProject      WHERE SolutionID = @prmSolutionID
    DELETE FROM dbo.SolutionConversation WHERE SolutionID = @prmSolutionID
    DELETE FROM dbo.SolutionApiClient    WHERE SolutionID = @prmSolutionID
    DELETE FROM dbo.Solution             WHERE SolutionID = @prmSolutionID
    SET @rows = @@ROWCOUNT

    COMMIT TRANSACTION
END TRY
BEGIN CATCH
    IF @@TRANCOUNT > 0 ROLLBACK TRANSACTION
    DECLARE @msg NVARCHAR(2000) = ERROR_MESSAGE()
    RAISERROR(@msg, 16, 1)
    RETURN
END CATCH

SELECT @rows AS RowsAffected

GO
