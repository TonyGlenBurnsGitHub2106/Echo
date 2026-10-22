SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_SolutionConfigValue_Save') IS NOT NULL DROP PROCEDURE dbo.stp_SolutionConfigValue_Save
GO
CREATE PROCEDURE dbo.stp_SolutionConfigValue_Save
    @prmSolutionConfigPropertyID    INT,
    @prmSolutionConfigEnvironmentID INT,
    @prmValueText                   NVARCHAR(2000),
    @prmSourceTitle                 NVARCHAR(200),
    @prmValueNote                   NVARCHAR(2000),
    @prmAlsoSetIn                   NVARCHAR(4000),    -- the other places, separated by |
    @prmMemberCode                  VARCHAR(20)
AS

/*

TEST
----

    EXEC stp_SolutionConfigValue_Save 1, 1, N'localhost', N'claims-batch.properties', NULL, N'logback.xml|application.properties', 'felix'

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------------------------------------
09/21/2026  Felix        Story 3516 Solution configuration: set one cell -- a property in an environment: its value, where it is
                         read from, a note, and the other places it is also set (a | separated list). Sources are a lookup
                         (find-or-create). A SECRET property never keeps a value: it is stored NULL whatever was sent. A cell
                         with nothing in it at all is removed rather than kept empty. The property and the environment must
                         belong to the same solution. Returns RowsAffected (0 when the cell was cleared).

*/

SET NOCOUNT ON

DECLARE @propSolution INT, @envSolution INT, @isSecret BIT
SELECT @propSolution = g.SolutionID, @isSecret = t.IsSecret
FROM   dbo.SolutionConfigProperty p
JOIN   dbo.SolutionConfigGroup g ON g.SolutionConfigGroupID = p.SolutionConfigGroupID
JOIN   dbo.SolutionConfigType t  ON t.SolutionConfigTypeID  = p.SolutionConfigTypeID
WHERE  p.SolutionConfigPropertyID = @prmSolutionConfigPropertyID
SELECT @envSolution = SolutionID FROM dbo.SolutionConfigEnvironment WHERE SolutionConfigEnvironmentID = @prmSolutionConfigEnvironmentID
IF @propSolution IS NULL BEGIN RAISERROR('That property no longer exists.', 16, 1) RETURN END
IF @envSolution  IS NULL BEGIN RAISERROR('That environment no longer exists.', 16, 1) RETURN END
IF @propSolution <> @envSolution BEGIN RAISERROR('That environment belongs to a different solution.', 16, 1) RETURN END

SET @prmValueText   = NULLIF(LTRIM(RTRIM(ISNULL(@prmValueText, N''))), N'')
SET @prmSourceTitle = NULLIF(LTRIM(RTRIM(ISNULL(@prmSourceTitle, N''))), N'')
SET @prmValueNote   = NULLIF(LTRIM(RTRIM(ISNULL(@prmValueNote, N''))), N'')
SET @prmAlsoSetIn   = NULLIF(LTRIM(RTRIM(ISNULL(@prmAlsoSetIn, N''))), N'')
IF @isSecret = 1 SET @prmValueText = NULL

DECLARE @sourceId INT = NULL
IF @prmSourceTitle IS NOT NULL EXEC dbo.stp_SolutionConfigSource_FindOrCreate @prmSourceTitle, @sourceId OUTPUT

-- The also-set list, split on | (no STRING_SPLIT at compatibility level 90), each find-or-created.
DECLARE @also TABLE (SourceID INT PRIMARY KEY)
DECLARE @rest NVARCHAR(4000) = @prmAlsoSetIn, @piece NVARCHAR(200), @cut INT, @oneId INT
WHILE @rest IS NOT NULL AND LEN(@rest) > 0 BEGIN
    SET @cut = CHARINDEX(N'|', @rest)
    IF @cut = 0 BEGIN SET @piece = @rest SET @rest = NULL END
    ELSE BEGIN SET @piece = LEFT(@rest, @cut - 1) SET @rest = SUBSTRING(@rest, @cut + 1, 4000) END
    SET @piece = LTRIM(RTRIM(@piece))
    IF @piece <> N'' BEGIN
        EXEC dbo.stp_SolutionConfigSource_FindOrCreate @piece, @oneId OUTPUT
        IF @oneId IS NOT NULL AND ISNULL(@oneId, 0) <> ISNULL(@sourceId, 0) AND NOT EXISTS (SELECT 1 FROM @also WHERE SourceID = @oneId)
            INSERT INTO @also (SourceID) VALUES (@oneId)
    END
END

DECLARE @memberId INT = NULL
IF ISNULL(@prmMemberCode, '') <> '' EXEC dbo.stp_SolutionMember_FindOrCreate @prmMemberCode, @memberId OUTPUT

DECLARE @valueId INT, @rows INT = 1
BEGIN TRY
    BEGIN TRANSACTION
    SELECT @valueId = SolutionConfigValueID FROM dbo.SolutionConfigValue WITH (UPDLOCK, HOLDLOCK)
    WHERE  SolutionConfigPropertyID = @prmSolutionConfigPropertyID AND SolutionConfigEnvironmentID = @prmSolutionConfigEnvironmentID

    IF @prmValueText IS NULL AND @sourceId IS NULL AND @prmValueNote IS NULL AND NOT EXISTS (SELECT 1 FROM @also) BEGIN
        IF @valueId IS NOT NULL BEGIN
            DELETE FROM dbo.SolutionConfigValueAlsoSet WHERE SolutionConfigValueID = @valueId
            DELETE FROM dbo.SolutionConfigValue        WHERE SolutionConfigValueID = @valueId
        END
        SET @rows = 0
    END ELSE BEGIN
        IF @valueId IS NULL BEGIN
            INSERT INTO dbo.SolutionConfigValue (SolutionConfigPropertyID, SolutionConfigEnvironmentID, ValueText, SolutionConfigSourceID, ValueNote, ModifiedByMemberID)
            VALUES (@prmSolutionConfigPropertyID, @prmSolutionConfigEnvironmentID, @prmValueText, @sourceId, @prmValueNote, @memberId)
            SET @valueId = SCOPE_IDENTITY()
        END ELSE
            UPDATE dbo.SolutionConfigValue
            SET    ValueText = @prmValueText, SolutionConfigSourceID = @sourceId, ValueNote = @prmValueNote,
                   ModifiedByMemberID = @memberId, ModifiedDate = GETDATE()
            WHERE  SolutionConfigValueID = @valueId
        DELETE a FROM dbo.SolutionConfigValueAlsoSet a
        WHERE  a.SolutionConfigValueID = @valueId AND NOT EXISTS (SELECT 1 FROM @also x WHERE x.SourceID = a.SolutionConfigSourceID)
        INSERT INTO dbo.SolutionConfigValueAlsoSet (SolutionConfigValueID, SolutionConfigSourceID)
        SELECT @valueId, x.SourceID FROM @also x
        WHERE  NOT EXISTS (SELECT 1 FROM dbo.SolutionConfigValueAlsoSet a WHERE a.SolutionConfigValueID = @valueId AND a.SolutionConfigSourceID = x.SourceID)
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
