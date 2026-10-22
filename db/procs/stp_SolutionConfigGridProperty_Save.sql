SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_SolutionConfigGridProperty_Save') IS NOT NULL DROP PROCEDURE dbo.stp_SolutionConfigGridProperty_Save
GO
CREATE PROCEDURE dbo.stp_SolutionConfigGridProperty_Save
    @prmSolutionConfigGridPropertyID INT,
    @prmSolutionConfigGridGroupID    INT,
    @prmPropertyKey                  NVARCHAR(200),
    @prmPropertyDescription          NVARCHAR(2000),
    @prmTypeCode                     VARCHAR(20),
    @prmSortOrder                    INT = NULL,
    @prmModifiedBy                   VARCHAR(40) = NULL
AS

/*

TEST
----

    EXEC stp_SolutionConfigGridProperty_Save 0, 1, N'logback.smtpHost', N'The mail relay', 'host', NULL, 'felix'

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------------------------------------
10/09/2026  Felix        Story 5611 Solution Configuration II: add or change a property (a row of the grid). The name is unique
                         per solution (by index -- the double-post guard); a property may move only to a group of the same
                         solution. The type is the shared SolutionConfigType lookup: making a property SECRET wipes its stored
                         values. Returns SolutionConfigGridPropertyID.

*/

SET NOCOUNT ON

SET @prmPropertyKey = LTRIM(RTRIM(ISNULL(@prmPropertyKey, N'')))
IF @prmPropertyKey = N'' BEGIN RAISERROR('A property needs a name. Nothing was saved.', 16, 1) RETURN END
SET @prmPropertyDescription = NULLIF(LTRIM(RTRIM(ISNULL(@prmPropertyDescription, N''))), N'')
SET @prmTypeCode = LOWER(LTRIM(RTRIM(ISNULL(@prmTypeCode, ''))))
IF @prmTypeCode = '' SET @prmTypeCode = 'text'

DECLARE @typeId INT, @isSecret BIT
SELECT @typeId = SolutionConfigTypeID, @isSecret = IsSecret FROM dbo.SolutionConfigType WHERE TypeCode = @prmTypeCode
IF @typeId IS NULL BEGIN
    DECLARE @badType NVARCHAR(300) = N'"' + @prmTypeCode + N'" is not a property type. Use text, path, host, port, url, number, flag or secret. Nothing was saved.'
    RAISERROR(@badType, 16, 1) RETURN
END

DECLARE @targetSolution INT = (SELECT SolutionID FROM dbo.SolutionConfigGridGroup WHERE SolutionConfigGridGroupID = @prmSolutionConfigGridGroupID)
DECLARE @oldGroup INT, @oldSolution INT
IF @prmSolutionConfigGridPropertyID > 0 BEGIN
    SELECT @oldGroup = SolutionConfigGridGroupID, @oldSolution = SolutionID
    FROM   dbo.SolutionConfigGridProperty WHERE SolutionConfigGridPropertyID = @prmSolutionConfigGridPropertyID
    IF @oldGroup IS NULL BEGIN RAISERROR('That property no longer exists. Nothing was saved.', 16, 1) RETURN END
    IF ISNULL(@prmSolutionConfigGridGroupID, 0) <= 0 BEGIN SET @prmSolutionConfigGridGroupID = @oldGroup SET @targetSolution = @oldSolution END
    IF ISNULL(@targetSolution, 0) <> @oldSolution BEGIN
        RAISERROR('A property can only move to a group of the same solution. Nothing was saved.', 16, 1) RETURN
    END
END ELSE IF @targetSolution IS NULL BEGIN
    RAISERROR('That group no longer exists. Nothing was saved.', 16, 1) RETURN
END

IF EXISTS (SELECT 1 FROM dbo.SolutionConfigGridProperty
           WHERE SolutionID = @targetSolution AND PropertyKey = @prmPropertyKey
           AND   SolutionConfigGridPropertyID <> ISNULL(@prmSolutionConfigGridPropertyID, 0)) BEGIN
    DECLARE @dup NVARCHAR(400) = N'This solution already has a property named "' + @prmPropertyKey + N'". Nothing was saved.'
    RAISERROR(@dup, 16, 1) RETURN
END

SET XACT_ABORT ON
BEGIN TRY
    BEGIN TRANSACTION
    IF @prmSolutionConfigGridPropertyID > 0 BEGIN
        IF @prmSolutionConfigGridGroupID <> @oldGroup AND @prmSortOrder IS NULL
            SET @prmSortOrder = ISNULL((SELECT MAX(SortOrder) FROM dbo.SolutionConfigGridProperty WHERE SolutionConfigGridGroupID = @prmSolutionConfigGridGroupID), 0) + 1
        UPDATE dbo.SolutionConfigGridProperty
        SET    SolutionConfigGridGroupID = @prmSolutionConfigGridGroupID, PropertyKey = @prmPropertyKey,
               PropertyDescription = @prmPropertyDescription, SolutionConfigTypeID = @typeId,
               SortOrder = ISNULL(@prmSortOrder, SortOrder), ModifiedDate = GETDATE(), ModifiedBy = @prmModifiedBy
        WHERE  SolutionConfigGridPropertyID = @prmSolutionConfigGridPropertyID
        IF @isSecret = 1
            UPDATE dbo.SolutionConfigGridValue SET ValueText = NULL, ModifiedDate = GETDATE()
            WHERE  SolutionConfigGridPropertyID = @prmSolutionConfigGridPropertyID AND ValueText IS NOT NULL
    END ELSE BEGIN
        INSERT INTO dbo.SolutionConfigGridProperty (SolutionID, SolutionConfigGridGroupID, PropertyKey, PropertyDescription, SolutionConfigTypeID, SortOrder, ModifiedBy)
        VALUES (@targetSolution, @prmSolutionConfigGridGroupID, @prmPropertyKey, @prmPropertyDescription, @typeId,
                ISNULL(@prmSortOrder, ISNULL((SELECT MAX(SortOrder) FROM dbo.SolutionConfigGridProperty WHERE SolutionConfigGridGroupID = @prmSolutionConfigGridGroupID), 0) + 1),
                @prmModifiedBy)
        SET @prmSolutionConfigGridPropertyID = SCOPE_IDENTITY()
    END
    COMMIT TRANSACTION
END TRY
BEGIN CATCH
    IF @@TRANCOUNT > 0 ROLLBACK TRANSACTION
    DECLARE @msg NVARCHAR(2000) = ERROR_MESSAGE()
    RAISERROR(@msg, 16, 1)
    RETURN
END CATCH
SELECT @prmSolutionConfigGridPropertyID AS SolutionConfigGridPropertyID

GO
