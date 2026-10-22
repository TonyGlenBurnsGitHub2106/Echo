SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_SolutionNote_Locate') IS NOT NULL DROP PROCEDURE dbo.stp_SolutionNote_Locate
GO
CREATE PROCEDURE dbo.stp_SolutionNote_Locate
    @prmSolutionNoteID INT
AS

/*

TEST
----

    EXEC stp_SolutionNote_Locate 1

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------------------------------------
09/17/2026  Felix        Story 3493: which solution a note lives in, so a link that knows only the note can open its page.

09/22/2026  Felix        Solutions documents are now NOTES (SolutionDocument* -> SolutionNote*, Tony 09/22/2026).

*/

SET NOCOUNT ON

SELECT SolutionNoteID, SolutionID FROM dbo.SolutionNote WHERE SolutionNoteID = @prmSolutionNoteID


GO
