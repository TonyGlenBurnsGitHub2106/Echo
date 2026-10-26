SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_ReaderPosition_Get') IS NOT NULL DROP PROCEDURE dbo.stp_ReaderPosition_Get
GO
CREATE PROCEDURE dbo.stp_ReaderPosition_Get
    @prmDocKey NVARCHAR(450)
AS

/*

TEST
----

    EXEC stp_ReaderPosition_Get @prmDocKey = 'file:Some Paper.pdf'

    -- Where the reader stopped in one document. Returns zero rows when the
    -- document has never been read, which the reader treats as "start at the
    -- top" rather than as an error.

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------------------------------------
08/28/2026  Felix        Read back one document's reading position. Story 2422.

*/

SET NOCOUNT ON

SELECT ReaderPositionID,
       DocKey,
       DocTitle,
       ViewMode,
       BlockIndex,
       CharOffset,
       PageNumber,
       ScrollRatio,
       PercentRead,
       CreatedDate,
       ModifiedDate
FROM   dbo.ReaderPosition
WHERE  DocKey = @prmDocKey

GO
