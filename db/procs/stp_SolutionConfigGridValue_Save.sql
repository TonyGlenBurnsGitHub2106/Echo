SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_SolutionConfigGridValue_Save') IS NOT NULL DROP PROCEDURE dbo.stp_SolutionConfigGridValue_Save
GO
CREATE PROCEDURE dbo.stp_SolutionConfigGridValue_Save
    @prmSolutionConfigGridPropertyID INT,
    @prmSolutionConfigGridColumnID   INT,
    @prmValueText                    NVARCHAR(2000),
    @prmSourceTitle                  NVARCHAR(200),
    @prmValueNote                    NVARCHAR(2000),
    @prmStateCode                    VARCHAR(20),
    @prmAlsoSetIn                    NVARCHAR(4000),    -- the other places, separated by |
    @prmMemberCode                   VARCHAR(20)
AS

/*

TEST
----

    EXEC stp_SolutionConfigGridValue_Save 1, 1, N'localhost', N'claims-batch.properties', NULL, 'planned', N'logback.xml', 'felix'

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------------------------------------
10/09/2026  Felix        Story 5611 Solution Configuration II: set one cell -- a property in a column: its value, where it is read
                         from, a note, its state (observed / planned / unverified; blank = observed) and the other places it is
                         also set (a | separated list). Sources are the shared find-or-create lookup. A SECRET property never
                         keeps a value. A cell with no value, no source, no note and no other places is removed. The property and
                         the column must belong to the same solution. Returns RowsAffected (0 when the cell was cleared).

*/

SET NOCOUNT ON

DECLARE @propSolution INT, @colSolution INT, @isSecret BIT
SELECT @propSolution = p.SolutionID, @isSecret = t.IsSecret
FROM   dbo.SolutionConfigGridProperty p
JOIN   dbo.SolutionConfigType t ON t.SolutionConfigTypeID = p.SolutionConfigTypeID
WHERE  p.SolutionConfigGridPropertyID = @prmSolutionConfigGridPropertyID
SELECT @colSolution = SolutionID FROM dbo.SolutionConfigGridColumn WHERE SolutionConfigGridColumnID = @prmSolutionConfigGridColumnID
IF @propSolution IS NULL BEGIN RAISERROR('That property no longer exists. Nothing was saved.', 16, 1) RETURN END
IF @colSolution  IS NULL BEGIN RAISERROR('That column no longer exists. Nothing was saved.', 16, 1) RETURN END
IF @propSolution <> @colSolution BEGIN RAISERROR('That column belongs to a different solution. Nothing was saved.', 16, 1) RETURN END

SET @prmStateCode = LOWER(LTRIM(RTRIM(ISNULL(@prmStateCode, ''))))
IF @prmStateCode = '' SET @prmStateCode = 'observed'
DECLARE @stateId INT = (SELECT SolutionConfigGridCellStateID FROM dbo.SolutionConfigGridCellState WHERE StateCode = @prmStateCode)
IF @stateId IS NULL BEGIN
    DECLARE @badState NVARCHAR(300) = N'"' + @prmStateCode + N'" is not a cell state. Use observed, planned or unverified. Nothing was saved.'
    RAISERROR(@badState, 16, 1) RETURN
END

SET @prmValueText   = NULLIF(LTRIM(RTRIM(ISNULL(@prmValueText, N''))), N'')
SET @prmSourceTitle = NULLIF(LTRIM(RTRIM(ISNULL(@prmSourceTitle, N''))), N'')
SET @prmValueNote   = NULLIF(LTRIM(RTRIM(ISNULL(@prmValueNote, N''))), N'')
SET @prmAlsoSetIn   = NULLIF(LTRIM(RTRIM(ISNULL(@prmAlsoSetIn, N''))), N'')
IF @isSecret = 1 SET @prmValueText = NULL

-- The also-set list, split on | (no STRING_SPLIT at compatibility level 90). Names only here;
-- they are find-or-created inside the transaction.
DECLARE @alsoNames TABLE (SourceTitle NVARCHAR(200) PRIMARY KEY)
DECLARE @rest NVARCHAR(4000) = @prmAlsoSetIn, @piece NVARCHAR(200), @cut INT
WHILE @rest IS NOT NULL AND LEN(@rest) > 0 BEGIN
    SET @cut = CHARINDEX(N'|', @rest)
    IF @cut = 0 BEGIN SET @piece = @rest SET @rest = NULL END
    ELSE BEGIN SET @piece = LEFT(@rest, @cut - 1) SET @rest = SUBSTRING(@rest, @cut + 1, 4000) END
    SET @piece = LTRIM(RTRIM(@piece))
    IF @piece <> N'' AND @piece <> ISNULL(@prmSourceTitle, N'') AND NOT EXISTS (SELECT 1 FROM @alsoNames WHERE SourceTitle = @piece)
        INSERT INTO @alsoNames (SourceTitle) VALUES (@piece)
END

DECLARE @memberId INT = NULL
IF ISNULL(@prmMemberCode, '') <> '' EXEC dbo.stp_SolutionMember_FindOrCreate @prmMemberCode, @memberId OUTPUT

DECLARE @valueId INT, @rows INT = 1, @sourceId INT = NULL, @oneId INT
SET XACT_ABORT ON
BEGIN TRY
    BEGIN TRANSACTION
    IF @prmSourceTitle IS NOT NULL EXEC dbo.stp_SolutionConfigSource_FindOrCreate @prmSourceTitle, @sourceId OUTPUT
    DECLARE @also TABLE (SourceID INT PRIMARY KEY)
    DECLARE alsoCur CURSOR LOCAL FAST_FORWARD FOR SELECT SourceTitle FROM @alsoNames
    OPEN alsoCur
    FETCH NEXT FROM alsoCur INTO @piece
    WHILE @@FETCH_STATUS = 0 BEGIN
        EXEC dbo.stp_SolutionConfigSource_FindOrCreate @piece, @oneId OUTPUT
        IF @oneId IS NOT NULL AND @oneId <> ISNULL(@sourceId, 0) AND NOT EXISTS (SELECT 1 FROM @also WHERE SourceID = @oneId)
            INSERT INTO @also (SourceID) VALUES (@oneId)
        FETCH NEXT FROM alsoCur INTO @piece
    END
    CLOSE alsoCur
    DEALLOCATE alsoCur

    SELECT @valueId = SolutionConfigGridValueID FROM dbo.SolutionConfigGridValue WITH (UPDLOCK, HOLDLOCK)
    WHERE  SolutionConfigGridPropertyID = @prmSolutionConfigGridPropertyID AND SolutionConfigGridColumnID = @prmSolutionConfigGridColumnID

    IF @prmValueText IS NULL AND @sourceId IS NULL AND @prmValueNote IS NULL AND NOT EXISTS (SELECT 1 FROM @also) BEGIN
        IF @valueId IS NOT NULL BEGIN
            DELETE FROM dbo.SolutionConfigGridValueAlsoSet WHERE SolutionConfigGridValueID = @valueId
            DELETE FROM dbo.SolutionConfigGridValue        WHERE SolutionConfigGridValueID = @valueId
        END
        SET @rows = 0
    END ELSE BEGIN
        IF @valueId IS NULL BEGIN
            INSERT INTO dbo.SolutionConfigGridValue (SolutionConfigGridPropertyID, SolutionConfigGridColumnID, ValueText, SolutionConfigSourceID, ValueNote,
                                                     SolutionConfigGridCellStateID, ModifiedByMemberID)
            VALUES (@prmSolutionConfigGridPropertyID, @prmSolutionConfigGridColumnID, @prmValueText, @sourceId, @prmValueNote, @stateId, @memberId)
            SET @valueId = SCOPE_IDENTITY()
        END ELSE
            UPDATE dbo.SolutionConfigGridValue
            SET    ValueText = @prmValueText, SolutionConfigSourceID = @sourceId, ValueNote = @prmValueNote,
                   SolutionConfigGridCellStateID = @stateId, ModifiedByMemberID = @memberId, ModifiedDate = GETDATE()
            WHERE  SolutionConfigGridValueID = @valueId
        DELETE a FROM dbo.SolutionConfigGridValueAlsoSet a
        WHERE  a.SolutionConfigGridValueID = @valueId AND NOT EXISTS (SELECT 1 FROM @also x WHERE x.SourceID = a.SolutionConfigSourceID)
        INSERT INTO dbo.SolutionConfigGridValueAlsoSet (SolutionConfigGridValueID, SolutionConfigSourceID)
        SELECT @valueId, x.SourceID FROM @also x
        WHERE  NOT EXISTS (SELECT 1 FROM dbo.SolutionConfigGridValueAlsoSet a WHERE a.SolutionConfigGridValueID = @valueId AND a.SolutionConfigSourceID = x.SourceID)
    END
    COMMIT TRANSACTION
END TRY
BEGIN CATCH
    IF @@TRANCOUNT > 0 ROLLBACK TRANSACTION
    DECLARE @msg NVARCHAR(2000) = ERROR_MESSAGE()
    RAISERROR(@msg, 16, 1)
    RETURN
END CATCH
SELECT @rows AS RowsAffected

GO
