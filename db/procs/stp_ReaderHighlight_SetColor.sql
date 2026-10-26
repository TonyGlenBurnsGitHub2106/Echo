SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_ReaderHighlight_SetColor') IS NOT NULL DROP PROCEDURE dbo.stp_ReaderHighlight_SetColor
GO
CREATE PROCEDURE dbo.stp_ReaderHighlight_SetColor
    @prmReaderHighlightID INT,
    @prmColorCode         NVARCHAR(20) = 'yellow'
AS

/*

TEST
----

    EXEC stp_ReaderHighlight_SetColor @prmReaderHighlightID = 12,
                                      @prmColorCode = 'blue'

    -- Recolours one saved highlight and returns the stored row, so the reader
    -- can repaint the mark from what the database actually holds. The colour
    -- vocabulary itself is owned by the web service layer; this proc only
    -- stores what it is given.

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------------------------------------
08/22/2026  Felix        Change the colour of one Leonardo Reader highlight. Story 2395.

*/

SET NOCOUNT ON

IF @prmReaderHighlightID IS NULL OR @prmReaderHighlightID <= 0 RETURN
IF @prmColorCode IS NULL OR LTRIM(RTRIM(@prmColorCode)) = '' SET @prmColorCode = 'yellow'

UPDATE dbo.ReaderHighlight
SET    ColorCode    = LTRIM(RTRIM(@prmColorCode)),
       ModifiedDate = GETDATE()
WHERE  ReaderHighlightID = @prmReaderHighlightID

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
WHERE  ReaderHighlightID = @prmReaderHighlightID

GO
