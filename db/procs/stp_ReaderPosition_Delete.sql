SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_ReaderPosition_Delete') IS NOT NULL DROP PROCEDURE dbo.stp_ReaderPosition_Delete
GO
CREATE PROCEDURE dbo.stp_ReaderPosition_Delete
    @prmDocKey NVARCHAR(450)
AS

/*

TEST
----

    EXEC stp_ReaderPosition_Delete @prmDocKey = 'file:Some Paper.pdf'

    -- Forget where the reader stopped in one document, so it opens at the top
    -- again. Used by the reader's "Start from the beginning" action. Removing a
    -- position never touches the document's highlights.

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------------------------------------
08/28/2026  Felix        Forget one document's reading position. Story 2422.

*/

SET NOCOUNT ON

DELETE FROM dbo.ReaderPosition
WHERE  DocKey = @prmDocKey

GO
