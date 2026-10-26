SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_ReaderHighlight_Delete') IS NOT NULL DROP PROCEDURE dbo.stp_ReaderHighlight_Delete
GO
CREATE PROCEDURE dbo.stp_ReaderHighlight_Delete
    @prmReaderHighlightID INT
AS

/*

TEST
----

    EXEC stp_ReaderHighlight_Delete @prmReaderHighlightID = 12

    -- Removes one saved highlight, and with it any notes written against that
    -- mark -- a note about a passage has no meaning once the mark is gone, and
    -- there is no foreign key to cascade for us. The excerpt the mark may have
    -- created on the Research shelf is deliberately left alone: unmarking the
    -- page does not throw away research the user already filed.

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------------------------------------
07/27/2026  Stewart      Delete one Leonardo Reader highlight. Story 2307.
08/26/2026  Stewart      Also clear the mark's notes so none are stranded. Story 2411.

*/

SET NOCOUNT ON

IF @prmReaderHighlightID IS NULL OR @prmReaderHighlightID <= 0 RETURN

DELETE FROM dbo.ReaderMarkNote
WHERE  ReaderHighlightID = @prmReaderHighlightID

DELETE FROM dbo.ReaderHighlight
WHERE  ReaderHighlightID = @prmReaderHighlightID

GO
