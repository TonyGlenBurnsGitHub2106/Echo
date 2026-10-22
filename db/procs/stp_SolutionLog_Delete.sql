SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_SolutionLog_Delete') IS NOT NULL DROP PROCEDURE dbo.stp_SolutionLog_Delete
GO
CREATE PROCEDURE dbo.stp_SolutionLog_Delete
    @prmSolutionLogID INT
AS

/*

TEST
----

    EXEC stp_SolutionLog_Delete 0

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------------------------------------
09/17/2026  Felix        Story 3493: remove one journal entry. Runbook steps promoted from it keep their text and lose
                         only the back-pointer. The gap it leaves in the sequence is deliberate: numbers are
                         never reused. Returns RowsAffected.

09/22/2026  Felix        Solutions documents are now NOTES (SolutionDocument* -> SolutionNote*, Tony 09/22/2026).

*/

SET NOCOUNT ON

UPDATE dbo.SolutionNoteStep SET SourceSolutionLogID = NULL WHERE SourceSolutionLogID = @prmSolutionLogID
DELETE FROM dbo.SolutionLog WHERE SolutionLogID = @prmSolutionLogID
SELECT @@ROWCOUNT AS RowsAffected


GO
