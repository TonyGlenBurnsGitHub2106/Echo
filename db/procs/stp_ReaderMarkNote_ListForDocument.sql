SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_ReaderMarkNote_ListForDocument') IS NOT NULL DROP PROCEDURE dbo.stp_ReaderMarkNote_ListForDocument
GO
CREATE PROCEDURE dbo.stp_ReaderMarkNote_ListForDocument
    @prmDocKey NVARCHAR(450)
AS

/*

TEST
----

    EXEC stp_ReaderMarkNote_ListForDocument @prmDocKey = 'file:Some Paper.pdf'

    -- Every note written against every mark in one document, in reading order
    -- (the mark's position first, then oldest note first inside a mark). The
    -- reader loads its marks and their notes together when a document opens, so
    -- writing a note never costs a second round trip per mark.

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------------------------------------
08/26/2026  Stewart      List the notes on one document's reading marks. Story 2411.

*/

SET NOCOUNT ON

SELECT n.ReaderMarkNoteID,
       n.ReaderHighlightID,
       n.NoteText,
       n.CreatedDate,
       n.ModifiedDate
FROM   dbo.ReaderMarkNote n
       INNER JOIN dbo.ReaderHighlight h ON h.ReaderHighlightID = n.ReaderHighlightID
WHERE  h.DocKey = @prmDocKey
ORDER BY h.BlockIndex, h.StartOffset, h.ReaderHighlightID, n.CreatedDate, n.ReaderMarkNoteID

GO
