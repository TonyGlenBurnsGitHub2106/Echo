SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_ReaderHighlight_ListForDocument') IS NOT NULL DROP PROCEDURE dbo.stp_ReaderHighlight_ListForDocument
GO
CREATE PROCEDURE dbo.stp_ReaderHighlight_ListForDocument
    @prmDocKey NVARCHAR(450)
AS

/*

TEST
----

    EXEC stp_ReaderHighlight_ListForDocument @prmDocKey = 'file:Some Paper.pdf'

    -- Every saved highlight for one document, in reading order (paragraph, then
    -- position inside the paragraph). The Leonardo Reader re-applies them to the
    -- reflowed text when the document is opened again.

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------------------------------------
07/27/2026  Stewart      List the saved Leonardo Reader highlights for one document. Story 2307.

*/

SET NOCOUNT ON

SELECT ReaderHighlightID,
       DocKey,
       DocTitle,
       ViewMode,
       PageNumber,
       BlockIndex,
       StartOffset,
       EndOffset,
       SelectedText,
       ColorCode,
       ResearchProjectID,
       ResearchNoteID,
       CreatedDate,
       ModifiedDate
FROM   dbo.ReaderHighlight
WHERE  DocKey = @prmDocKey
ORDER BY BlockIndex, StartOffset, ReaderHighlightID

GO
