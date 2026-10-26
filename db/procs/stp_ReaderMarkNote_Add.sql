SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_ReaderMarkNote_Add') IS NOT NULL DROP PROCEDURE dbo.stp_ReaderMarkNote_Add
GO
CREATE PROCEDURE dbo.stp_ReaderMarkNote_Add
    @prmReaderHighlightID INT,
    @prmNoteText          NVARCHAR(MAX)
AS

/*

TEST
----

    EXEC stp_ReaderMarkNote_Add @prmReaderHighlightID = 12,
                                @prmNoteText = 'This is the line the whole chapter turns on.'

    -- Writes one note against one reading mark (a bookmark or a highlight) and
    -- returns the stored row. A mark may carry any number of notes; nothing is
    -- overwritten. Returns no row when the mark does not exist, so a note can
    -- never be written against a mark that has already been removed.

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------------------------------------
08/26/2026  Stewart      Add one note to a reading mark. Story 2411.

*/

SET NOCOUNT ON

-- Every path falls through to the SELECT below, even the refused ones. An EXEC
-- that returns NO result set at all makes the JDBC caller throw rather than see
-- an empty answer, so "we would not write that" has to be reported as zero rows.
DECLARE @intNewID INT

IF @prmReaderHighlightID IS NOT NULL AND @prmReaderHighlightID > 0
   AND @prmNoteText IS NOT NULL AND LTRIM(RTRIM(@prmNoteText)) <> ''
   AND EXISTS (SELECT 1 FROM dbo.ReaderHighlight WHERE ReaderHighlightID = @prmReaderHighlightID)
BEGIN
    INSERT INTO dbo.ReaderMarkNote (ReaderHighlightID, NoteText)
    VALUES (@prmReaderHighlightID, LTRIM(RTRIM(@prmNoteText)))

    SET @intNewID = SCOPE_IDENTITY()
END

SELECT ReaderMarkNoteID,
       ReaderHighlightID,
       NoteText,
       CreatedDate,
       ModifiedDate
FROM   dbo.ReaderMarkNote
WHERE  ReaderMarkNoteID = @intNewID

GO
