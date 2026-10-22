SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_SolutionNoteType_List') IS NOT NULL DROP PROCEDURE dbo.stp_SolutionNoteType_List
GO
CREATE PROCEDURE dbo.stp_SolutionNoteType_List
AS

/*

TEST
----

    EXEC stp_SolutionNoteType_List

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------------------------------------
09/17/2026  Felix        Story 3493: the kinds of note (runbook, strategy, reference), in display order.

09/22/2026  Felix        Solutions documents are now NOTES (SolutionDocument* -> SolutionNote*, Tony 09/22/2026).

*/

SET NOCOUNT ON

SELECT SolutionNoteTypeID, NoteTypeCode, NoteTypeName, SortOrder
FROM   dbo.SolutionNoteType
ORDER BY SortOrder, SolutionNoteTypeID


GO
