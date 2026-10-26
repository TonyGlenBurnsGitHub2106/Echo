SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_ReaderPosition_Save') IS NOT NULL DROP PROCEDURE dbo.stp_ReaderPosition_Save
GO
CREATE PROCEDURE dbo.stp_ReaderPosition_Save
    @prmDocKey      NVARCHAR(450),
    @prmDocTitle    NVARCHAR(400) = NULL,
    @prmViewMode    NVARCHAR(40)  = NULL,
    @prmBlockIndex  INT           = 0,
    @prmCharOffset  INT           = 0,
    @prmPageNumber  INT           = NULL,
    @prmScrollRatio DECIMAL(9,6)  = 0,
    @prmPercentRead INT           = 0
AS

/*

TEST
----

    EXEC stp_ReaderPosition_Save @prmDocKey = 'file:Some Paper.pdf',
                                 @prmDocTitle = 'Some Paper.pdf',
                                 @prmViewMode = 'reader',
                                 @prmBlockIndex = 42, @prmCharOffset = 17,
                                 @prmPageNumber = 4,
                                 @prmScrollRatio = 0.463000, @prmPercentRead = 46

    -- Records where the reader stopped in one document and returns the stored
    -- row. A document has exactly ONE place you stopped, so a second call for
    -- the same DocKey replaces the first rather than piling up history -- the
    -- _LOG table keeps the trail.

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------------------------------------
08/28/2026  Felix        Save one document's reading position. Story 2422.

*/

SET NOCOUNT ON

-- Every path falls through to the SELECT below, even the refused one. An EXEC
-- that returns NO result set at all makes the JDBC caller throw rather than see
-- an empty answer, so "we would not store that" has to be reported as zero rows.
IF @prmDocKey IS NOT NULL AND LTRIM(RTRIM(@prmDocKey)) <> ''
BEGIN
    SET @prmDocKey = LTRIM(RTRIM(@prmDocKey))

    IF @prmBlockIndex  IS NULL OR @prmBlockIndex  < 0 SET @prmBlockIndex  = 0
    IF @prmCharOffset  IS NULL OR @prmCharOffset  < 0 SET @prmCharOffset  = 0
    IF @prmScrollRatio IS NULL OR @prmScrollRatio < 0 SET @prmScrollRatio = 0
    IF @prmScrollRatio > 1 SET @prmScrollRatio = 1
    IF @prmPercentRead IS NULL OR @prmPercentRead < 0 SET @prmPercentRead = 0
    IF @prmPercentRead > 100 SET @prmPercentRead = 100
    IF @prmPageNumber IS NOT NULL AND @prmPageNumber <= 0 SET @prmPageNumber = NULL

    IF EXISTS (SELECT 1 FROM dbo.ReaderPosition WHERE DocKey = @prmDocKey)
    BEGIN
        UPDATE dbo.ReaderPosition
        SET    DocTitle     = @prmDocTitle,
               ViewMode     = @prmViewMode,
               BlockIndex   = @prmBlockIndex,
               CharOffset   = @prmCharOffset,
               PageNumber   = @prmPageNumber,
               ScrollRatio  = @prmScrollRatio,
               PercentRead  = @prmPercentRead,
               ModifiedDate = GETDATE()
        WHERE  DocKey = @prmDocKey
    END
    ELSE
    BEGIN
        INSERT INTO dbo.ReaderPosition (DocKey, DocTitle, ViewMode, BlockIndex, CharOffset,
                                        PageNumber, ScrollRatio, PercentRead)
        VALUES (@prmDocKey, @prmDocTitle, @prmViewMode, @prmBlockIndex, @prmCharOffset,
                @prmPageNumber, @prmScrollRatio, @prmPercentRead)
    END
END

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
