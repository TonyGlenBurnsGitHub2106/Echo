SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_SolutionConfigProperty_Save') IS NOT NULL DROP PROCEDURE dbo.stp_SolutionConfigProperty_Save
GO
CREATE PROCEDURE dbo.stp_SolutionConfigProperty_Save
    @prmSolutionConfigPropertyID INT,
    @prmSolutionConfigGroupID    INT,
    @prmPropertyKey              NVARCHAR(200),
    @prmPropertyDescription      NVARCHAR(2000),
    @prmTypeCode                 VARCHAR(20),
    @prmSortOrder                INT         = NULL,   -- NULL = keep (edit, same group) / last
    @prmModifiedBy               VARCHAR(40) = NULL
AS

/*

TEST
----

    EXEC stp_SolutionConfigProperty_Save 0, 1, N'logback.smtpHost', N'The mail relay alerts go through', 'host', NULL, 'felix'

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------------------------------------
09/21/2026  Felix        Story 3516 Solution configuration: add (id = 0) or edit one property. A key appears once per solution.
                         An edit may move the property to another group of the same solution (to the end of it unless a sort
                         order is given). An unknown type code is refused rather than guessed. Turning a property into a SECRET
                         clears every stored value of it. Returns SolutionConfigPropertyID.

*/

SET NOCOUNT ON

SET @prmPropertyKey = LTRIM(RTRIM(ISNULL(@prmPropertyKey, N'')))
IF @prmPropertyKey = N'' BEGIN RAISERROR('A property needs a name.', 16, 1) RETURN END
SET @prmPropertyDescription = NULLIF(LTRIM(RTRIM(ISNULL(@prmPropertyDescription, N''))), N'')
SET @prmTypeCode = LOWER(LTRIM(RTRIM(ISNULL(@prmTypeCode, ''))))
IF @prmTypeCode = '' SET @prmTypeCode = 'text'

DECLARE @typeId INT, @isSecret BIT
SELECT @typeId = SolutionConfigTypeID, @isSecret = IsSecret FROM dbo.SolutionConfigType WHERE TypeCode = @prmTypeCode
IF @typeId IS NULL BEGIN
    DECLARE @badType NVARCHAR(300) = N'"' + @prmTypeCode + N'" is not a property type. Use text, path, host, port, url, number, flag or secret.'
    RAISERROR(@badType, 16, 1) RETURN
END

DECLARE @targetSolution INT = (SELECT SolutionID FROM dbo.SolutionConfigGroup WHERE SolutionConfigGroupID = @prmSolutionConfigGroupID)
DECLARE @oldGroup INT, @oldSolution INT
IF @prmSolutionConfigPropertyID > 0 BEGIN
    SELECT @oldGroup = p.SolutionConfigGroupID, @oldSolution = g.SolutionID
    FROM   dbo.SolutionConfigProperty p JOIN dbo.SolutionConfigGroup g ON g.SolutionConfigGroupID = p.SolutionConfigGroupID
    WHERE  p.SolutionConfigPropertyID = @prmSolutionConfigPropertyID
    IF @oldGroup IS NULL BEGIN RAISERROR('That property no longer exists.', 16, 1) RETURN END
    IF ISNULL(@prmSolutionConfigGroupID, 0) <= 0 BEGIN SET @prmSolutionConfigGroupID = @oldGroup SET @targetSolution = @oldSolution END
    IF ISNULL(@targetSolution, 0) <> @oldSolution BEGIN
        RAISERROR('A property can only move to a group of the same solution.', 16, 1) RETURN
    END
END ELSE IF @targetSolution IS NULL BEGIN
    RAISERROR('That group no longer exists.', 16, 1) RETURN
END

IF EXISTS (SELECT 1 FROM dbo.SolutionConfigProperty p
           JOIN dbo.SolutionConfigGroup g ON g.SolutionConfigGroupID = p.SolutionConfigGroupID
           WHERE g.SolutionID = @targetSolution AND p.PropertyKey = @prmPropertyKey
           AND   p.SolutionConfigPropertyID <> ISNULL(@prmSolutionConfigPropertyID, 0)) BEGIN
    DECLARE @dup NVARCHAR(400) = N'This solution already has a property named "' + @prmPropertyKey + N'".'
    RAISERROR(@dup, 16, 1) RETURN
END

BEGIN TRY
    BEGIN TRANSACTION
    IF @prmSolutionConfigPropertyID > 0 BEGIN
        IF @prmSolutionConfigGroupID <> @oldGroup AND @prmSortOrder IS NULL
            SET @prmSortOrder = ISNULL((SELECT MAX(SortOrder) FROM dbo.SolutionConfigProperty WHERE SolutionConfigGroupID = @prmSolutionConfigGroupID), 0) + 1
        UPDATE dbo.SolutionConfigProperty
        SET    SolutionConfigGroupID = @prmSolutionConfigGroupID, PropertyKey = @prmPropertyKey,
               PropertyDescription = @prmPropertyDescription, SolutionConfigTypeID = @typeId,
               SortOrder = ISNULL(@prmSortOrder, SortOrder), ModifiedDate = GETDATE(), ModifiedBy = @prmModifiedBy
        WHERE  SolutionConfigPropertyID = @prmSolutionConfigPropertyID
        IF @isSecret = 1
            UPDATE dbo.SolutionConfigValue SET ValueText = NULL, ModifiedDate = GETDATE()
            WHERE  SolutionConfigPropertyID = @prmSolutionConfigPropertyID AND ValueText IS NOT NULL
    END ELSE BEGIN
        INSERT INTO dbo.SolutionConfigProperty (SolutionConfigGroupID, PropertyKey, PropertyDescription, SolutionConfigTypeID, SortOrder, ModifiedBy)
        VALUES (@prmSolutionConfigGroupID, @prmPropertyKey, @prmPropertyDescription, @typeId,
                ISNULL(@prmSortOrder, ISNULL((SELECT MAX(SortOrder) FROM dbo.SolutionConfigProperty WHERE SolutionConfigGroupID = @prmSolutionConfigGroupID), 0) + 1),
                @prmModifiedBy)
        SET @prmSolutionConfigPropertyID = SCOPE_IDENTITY()
    END
    COMMIT TRANSACTION
END TRY
BEGIN CATCH
    IF @@TRANCOUNT > 0 ROLLBACK TRANSACTION
    DECLARE @msg NVARCHAR(2000) = ERROR_MESSAGE()
    RAISERROR(@msg, 16, 1)
    RETURN
END CATCH

SELECT @prmSolutionConfigPropertyID AS SolutionConfigPropertyID

GO
