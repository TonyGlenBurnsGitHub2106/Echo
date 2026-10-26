SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_ReaderMarkNote_Update') IS NOT NULL DROP PROCEDURE dbo.stp_ReaderMarkNote_Update
GO
CREATE PROCEDURE dbo.stp_ReaderMarkNote_Update
    @prmReaderMarkNoteID INT,
    @prmNoteText         NVARCHAR(MAX)
AS

/*

TEST
----

    EXEC stp_ReaderMarkNote_Update @prmReaderMarkNoteID = 3,
                                   @prmNoteText = 'Second thoughts -- it turns on the line after.'

    -- Rewrites one note and returns the stored row. Blank text is refused
    -- rather than saved: emptying a note is a delete, and the caller has to say
    -- so, or a mis-tap would silently wipe what the reader wrote.

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------------------------------------
08/26/2026  Stewart      Edit one note on a reading mark. Story 2411.

*/

SET NOCOUNT ON

-- As with the Add proc: a refused edit still has to answer with an empty row
-- set, because an EXEC that returns no result set at all makes JDBC throw.
IF @prmReaderMarkNoteID IS NULL SET @prmReaderMarkNoteID = 0
IF @prmNoteText IS NULL OR LTRIM(RTRIM(@prmNoteText)) = '' SET @prmReaderMarkNoteID = 0

UPDATE dbo.ReaderMarkNote
SET    NoteText     = LTRIM(RTRIM(@prmNoteText)),
       ModifiedDate = GETDATE()
WHERE  ReaderMarkNoteID = @prmReaderMarkNoteID

SELECT ReaderMarkNoteID,
       ReaderHighlightID,
       NoteText,
       CreatedDate,
       ModifiedDate
FROM   dbo.ReaderMarkNote
WHERE  ReaderMarkNoteID = @prmReaderMarkNoteID

GO
