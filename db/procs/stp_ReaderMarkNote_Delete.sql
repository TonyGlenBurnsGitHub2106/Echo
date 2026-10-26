SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_ReaderMarkNote_Delete') IS NOT NULL DROP PROCEDURE dbo.stp_ReaderMarkNote_Delete
GO
CREATE PROCEDURE dbo.stp_ReaderMarkNote_Delete
    @prmReaderMarkNoteID INT
AS

/*

TEST
----

    EXEC stp_ReaderMarkNote_Delete @prmReaderMarkNoteID = 3

    -- Removes one note. The mark it was written against is left alone -- taking
    -- back what you wrote about a passage is not the same as unmarking it.

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------------------------------------
08/26/2026  Stewart      Delete one note from a reading mark. Story 2411.

*/

SET NOCOUNT ON

IF @prmReaderMarkNoteID IS NULL OR @prmReaderMarkNoteID <= 0 RETURN

DELETE FROM dbo.ReaderMarkNote
WHERE  ReaderMarkNoteID = @prmReaderMarkNoteID

GO
