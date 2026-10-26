SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_ReaderHighlight_Add') IS NOT NULL DROP PROCEDURE dbo.stp_ReaderHighlight_Add
GO
CREATE PROCEDURE dbo.stp_ReaderHighlight_Add
    @prmDocKey            NVARCHAR(450),
    @prmDocTitle          NVARCHAR(400) = NULL,
    @prmViewMode          NVARCHAR(20)  = 'reader',
    @prmPageNumber        INT           = NULL,
    @prmBlockIndex        INT           = 0,
    @prmStartOffset       INT           = 0,
    @prmEndOffset         INT           = 0,
    @prmSelectedText      NVARCHAR(MAX),
    @prmColorCode         NVARCHAR(20)  = 'yellow',
    @prmResearchProjectID INT           = NULL,
    @prmResearchNoteID    INT           = NULL
AS

/*

TEST
----

    EXEC stp_ReaderHighlight_Add @prmDocKey = 'file:Some Paper.pdf',
                                 @prmDocTitle = 'Some Paper.pdf',
                                 @prmViewMode = 'reader',
                                 @prmPageNumber = 3,
                                 @prmBlockIndex = 12,
                                 @prmStartOffset = 40,
                                 @prmEndOffset = 190,
                                 @prmSelectedText = 'the marked sentence',
                                 @prmColorCode = 'yellow'

    -- Saves one marked stretch of text in one document and returns the new row.
    -- @prmResearchProjectID / @prmResearchNoteID are supplied only when the mark
    -- was also saved to the Research shelf as an excerpt.

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------------------------------------
07/27/2026  Stewart      Save one Leonardo Reader highlight. Story 2307.

*/

SET NOCOUNT ON

IF @prmDocKey IS NULL OR LTRIM(RTRIM(@prmDocKey)) = '' RETURN
IF @prmSelectedText IS NULL OR LTRIM(RTRIM(@prmSelectedText)) = '' RETURN

IF @prmViewMode IS NULL OR LTRIM(RTRIM(@prmViewMode)) = '' SET @prmViewMode = 'reader'
IF @prmColorCode IS NULL OR LTRIM(RTRIM(@prmColorCode)) = '' SET @prmColorCode = 'yellow'
IF @prmBlockIndex  IS NULL SET @prmBlockIndex  = 0
IF @prmStartOffset IS NULL SET @prmStartOffset = 0
IF @prmEndOffset   IS NULL SET @prmEndOffset   = 0
IF @prmResearchProjectID IS NOT NULL AND @prmResearchProjectID <= 0 SET @prmResearchProjectID = NULL
IF @prmResearchNoteID    IS NOT NULL AND @prmResearchNoteID    <= 0 SET @prmResearchNoteID    = NULL

DECLARE @intNewID INT

INSERT INTO dbo.ReaderHighlight
    (DocKey, DocTitle, ViewMode, PageNumber, BlockIndex, StartOffset, EndOffset,
     SelectedText, ColorCode, ResearchProjectID, ResearchNoteID)
VALUES
    (LTRIM(RTRIM(@prmDocKey)), @prmDocTitle, LTRIM(RTRIM(@prmViewMode)), @prmPageNumber,
     @prmBlockIndex, @prmStartOffset, @prmEndOffset,
     @prmSelectedText, LTRIM(RTRIM(@prmColorCode)), @prmResearchProjectID, @prmResearchNoteID)

SET @intNewID = SCOPE_IDENTITY()

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
WHERE  ReaderHighlightID = @intNewID

GO
