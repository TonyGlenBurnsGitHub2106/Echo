SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_ReaderMarkNote_DeleteForMark') IS NOT NULL DROP PROCEDURE dbo.stp_ReaderMarkNote_DeleteForMark
GO
CREATE PROCEDURE dbo.stp_ReaderMarkNote_DeleteForMark
    @prmReaderHighlightID INT
AS

/*

TEST
----

    EXEC stp_ReaderMarkNote_DeleteForMark @prmReaderHighlightID = 12

    -- Removes every note written against one reading mark. Called just before
    -- the mark itself is removed, so a note is never left pointing at a mark
    -- that is gone. Safe to run twice; the mark's own delete proc does the same
    -- thing for callers that go straight at it.

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------------------------------------
08/26/2026  Stewart      Delete every note on one reading mark. Story 2411.

*/

SET NOCOUNT ON

IF @prmReaderHighlightID IS NULL OR @prmReaderHighlightID <= 0 RETURN

DELETE FROM dbo.ReaderMarkNote
WHERE  ReaderHighlightID = @prmReaderHighlightID

GO
